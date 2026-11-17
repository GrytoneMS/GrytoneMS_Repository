// fetch 가로채기·상태·정적 렌더 검증. 실제 관리자 계정/DB/브라우저 검증이 아닙니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_ADMIN_MOCK = 'false'
const originalFetch = globalThis.fetch
const config = { server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' }
const server = await createServer(config)
let mockServer, passed = 0
const check = async (name, run) => { await run(); console.log(`PASS ${++passed} ${name}`) }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const clone = value => structuredClone(value)
try {
  const { realAdmin, validatePage } = await server.ssrLoadModule('/src/features/admin/api.ts')
  const { createAdminController } = await server.ssrLoadModule('/src/features/admin/controller.ts')
  const { allowedActions, jobError } = await server.ssrLoadModule('/src/features/admin/model.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const account = { id: 1, nickname: '예시', role: 'ADMIN' }, signal = new AbortController().signal
  const school = { id: 1, name: '테스트', campus: null, emailDomain: 'test.example', region: 'ASAN' }
  const report = { id: 10, targetType: 'POST', targetId: 20, target: { title: '대상', content: '<script>unsafe</script>', authorId: 30, hidden: true, deleted: false }, reasonCode: 'FAKE', detail: null, reporter: { id: 40, nickname: null }, status: 'RECEIVED', action: null, pendingCountForTarget: 2, createdAt: '2026-11-16T10:00:00', handledAt: null }
  const verification = { id: 11, user: { id: 50, nickname: '예시', email: 'demo@example.invalid' }, school: { id: 1, name: '테스트', campus: null }, status: 'PENDING', documentUrl: '/api/admin/verifications/11/document', rejectReason: null, requestedAt: '2026-11-16T10:00:00', reviewedAt: null }
  const envelope = data => new Response(JSON.stringify({ success: true, data }))
  const failure = (status, code) => new ApiError(status, { success: false, code, message: 'test' })
  const page = (items = [clone(report)], nextCursor = null) => ({ items, nextCursor, hasNext: nextCursor !== null })
  const base = () => ({ account: async () => account, list: async () => page(), submit: async () => {}, document: async () => new Blob(['demo'], { type: 'image/png' }) })
  const job = { kind: 'resolve', id: 10, action: 'NONE', days: 7, reason: '' }
  await check('무토큰·공백·권한 미확인/일반 사용자 보호 요청 fetch 0·권한 확인 전 화면 차단', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; return envelope({ ...account, role: 'USER' }) }
    for (const token of [null, '', ' ']) {
      const s = realAdmin(token)
      await assert.rejects(s.account(signal)); await assert.rejects(s.list('reports', 'RECEIVED', undefined, signal)); await assert.rejects(s.submit(job, signal)); await assert.rejects(s.document(11, signal))
    }
    assert.equal(calls, 0)
    const s = realAdmin('test'); await assert.rejects(s.list('reports', 'RECEIVED', undefined, signal)); assert.equal(calls, 0)
    await s.account(signal); await assert.rejects(s.submit(job, signal)); assert.equal(calls, 1)
    const pending = deferred(), c = createAdminController({ ...base(), account: () => pending.promise }, 'reports', 'RECEIVED')
    const start = c.start(); assert.equal(c.getSnapshot().access, 'checking'); assert.equal(c.getSnapshot().items.length, 0)
    pending.resolve({ ...account, role: 'USER' }); await start; assert.equal(c.getSnapshot().access, 'denied'); c.dispose()
  })
  await check('목록 경로·상태·size20·최초 커서 생략·Long 커서 문자열·인증/signal/no-store', async () => {
    globalThis.fetch = async () => envelope(account); const s = realAdmin('test'); await s.account(signal)
    for (const [menu, status, cursor] of [['reports', 'RECEIVED', undefined], ['reports', 'RESOLVED', '9223372036854775807'], ['verifications', 'PENDING', '1'], ['suspensions', '', undefined]]) {
      globalThis.fetch = async (url, options) => {
        const parsed = new URL(url); assert.equal(parsed.pathname, `/api/admin/${menu === 'verifications' ? 'verifications' : 'reports'}`)
        assert.equal(parsed.searchParams.get('size'), '20'); assert.equal(parsed.searchParams.get('status'), status || null); assert.equal(parsed.searchParams.get('cursor'), cursor ?? null)
        assert.equal(options.headers.get('Authorization'), 'Bearer test'); assert.equal(options.signal, signal); assert.equal(options.cache, 'no-store')
        return envelope(page([]))
      }
      await s.list(menu, status, cursor, signal)
    }
    globalThis.fetch = async (url) => { assert.equal(new URL(url).pathname, '/api/schools'); return envelope([school]) }
    assert.equal((await s.list('schools', '', undefined, signal)).items.length, 1)
    for (const value of [page([report, report]), { items: [], hasNext: true, nextCursor: '10' }, page([{ ...verification, documentUrl: 'https://external.invalid/private' }])]) assert.throws(() => validatePage(value, value.items[0]?.user ? 'verifications' : 'reports'))
  })
  await check('신고 대상별 허용 조치·같은 대상 처리 응답·7/30/영구 정지 요청 계약', async () => {
    assert.deepEqual(allowedActions(report), ['NONE', 'HIDE', 'DELETE', 'SUSPEND'])
    for (const targetType of ['USER', 'APPLICATION']) assert.deepEqual(allowedActions({ ...report, targetType }), ['NONE', 'SUSPEND'])
    assert.deepEqual(allowedActions({ ...report, status: 'RESOLVED' }), [])
    globalThis.fetch = async () => envelope(account); const s = realAdmin('test'); await s.account(signal)
    for (const days of [7, 30, null]) {
      globalThis.fetch = async (url, options) => {
        assert.equal(options.method, 'PATCH'); assert.equal(new URL(url).pathname, '/api/admin/reports/10/resolve')
        assert.deepEqual(JSON.parse(options.body), { action: 'SUSPEND', suspendDays: days, suspendReason: '사유' })
        return envelope({ reportId: 10, action: 'SUSPEND', resolvedCount: 2 })
      }
      await s.submit({ ...job, action: 'SUSPEND', days, reason: ' 사유 ' }, signal)
      globalThis.fetch = async (url, options) => {
        assert.equal(options.method, 'POST'); assert.equal(new URL(url).pathname, '/api/admin/users/30/suspension')
        assert.deepEqual(JSON.parse(options.body), { days, reason: '사유', reportId: 10 }); return envelope({ userId: 30, status: 'SUSPENDED', endsAt: null })
      }
      await s.submit({ kind: 'suspend', id: 30, reportId: 10, days, reason: ' 사유 ' }, signal)
    }
    assert.ok(jobError({ kind: 'suspend', id: 30, days: 3, reason: '사유' })); assert.ok(jobError({ kind: 'suspend', id: 30, days: 7, reason: ' ' }))
  })
  await check('승인·반려 PATCH 본문 구분·반려 사유·잘못된 성공 응답 차단', async () => {
    globalThis.fetch = async () => envelope(account); const s = realAdmin('test'); await s.account(signal)
    for (const kind of ['approve', 'reject']) {
      globalThis.fetch = async (url, options) => { assert.equal(new URL(url).pathname, `/api/admin/verifications/11/${kind}`); assert.equal(options.method, 'PATCH'); assert.deepEqual(options.body ? JSON.parse(options.body) : undefined, kind === 'reject' ? { reason: '반려 이유' } : undefined); return envelope({ ...verification, status: kind === 'approve' ? 'APPROVED' : 'REJECTED', documentUrl: null }) }
      await s.submit({ kind, id: 11, reason: ' 반려 이유 ' }, signal)
    }
    globalThis.fetch = async () => envelope(verification)
    await assert.rejects(s.submit({ kind: 'approve', id: 11, reason: '' }, signal), e => e.code === 'INVALID_RESPONSE')
    assert.ok(jobError({ kind: 'reject', id: 11, reason: ' ' }))
  })
  await check('학교 등록·수정·비활성화 경로/본문·정규화·지역 수정 미전송·검증', async () => {
    globalThis.fetch = async () => envelope(account); const s = realAdmin('test'); await s.account(signal)
    for (const kind of ['createSchool', 'updateSchool', 'deactivateSchool']) {
      globalThis.fetch = async (url, options) => {
        assert.equal(new URL(url).pathname, `/api/admin/schools${kind === 'createSchool' ? '' : kind === 'updateSchool' ? '/1' : '/1/deactivate'}`)
        assert.equal(options.method, kind === 'createSchool' ? 'POST' : 'PATCH')
        if (kind === 'deactivateSchool') { assert.equal(options.body, undefined); return envelope(null) }
        const body = JSON.parse(options.body); assert.equal(body.emailDomain, 'test.example'); assert.equal(body.name, '테스트'); assert.equal(Object.hasOwn(body, 'region'), kind === 'createSchool'); return envelope(school)
      }
      await s.submit({ kind, id: 1, school: { name: ' 테스트 ', campus: '', emailDomain: 'TEST.EXAMPLE', region: 'ASAN' } }, signal)
    }
    for (const patch of [{ name: ' ' }, { campus: 'a'.repeat(31) }, { emailDomain: 'bad@domain' }]) assert.ok(jobError({ kind: 'createSchool', id: 0, school: { ...school, campus: '', ...patch } }))
  })
  await check('서류 Blob 인증·no-store·외부 URL 미사용·종류/크기 검증·401/403 처리', async () => {
    globalThis.fetch = async () => envelope(account); const s = realAdmin('test'); await s.account(signal)
    globalThis.fetch = async (url, options) => { assert.equal(new URL(url).pathname, '/api/admin/verifications/11/document'); assert.equal(options.headers.Authorization, 'Bearer test'); assert.equal(options.cache, 'no-store'); assert.equal(options.redirect, 'error'); return new Response('png-bytes', { headers: { 'Content-Type': 'image/png' } }) }
    assert.ok((await s.document(11, signal)).size)
    globalThis.fetch = async () => new Response('<svg/>', { headers: { 'Content-Type': 'image/svg+xml' } }); await assert.rejects(s.document(11, signal))
    for (const status of [401, 403]) { globalThis.fetch = async () => new Response('', { status }); await assert.rejects(s.document(11, signal), e => e.status === status) }
  })
  await check('확정 전 요청 없음·같은 tick 중복 1회·성공 후 재조회·서류 제거', async () => {
    const pending = deferred(); let calls = 0, changed = false, loads = 0
    const c = createAdminController({ ...base(), list: async () => { loads++; return page(changed ? [] : [verification]) }, submit: async () => { calls++; await pending.promise; changed = true } }, 'verifications', 'PENDING')
    await c.start(); await c.showDocument(11); assert.ok(c.getSnapshot().document); assert.equal(calls, 0)
    const first = c.submit({ kind: 'approve', id: 11, reason: '' }); await c.submit({ kind: 'approve', id: 11, reason: '' }); assert.equal(calls, 1)
    pending.resolve(); await first; assert.equal(c.getSnapshot().document, null); assert.equal(c.getSnapshot().items.length, 0); assert.equal(loads, 2); assert.equal(c.getSnapshot().busy, false); c.dispose()
  })
  await check('처리 실패 입력 원본 유지·401/403 민감 목록 제거·권한 실패 가짜 성공 없음', async () => {
    for (const status of [400, 401, 403, 409, 500]) {
      const draft = { ...job }; const c = createAdminController({ ...base(), submit: async () => { throw failure(status, 'INVALID_INPUT') } }, 'reports', 'RECEIVED')
      await c.start(); await c.submit(draft); assert.deepEqual(draft, job); assert.equal(c.getSnapshot().notice, '')
      assert.equal(c.getSnapshot().items.length, [401, 403].includes(status) ? 0 : 1)
      if (status === 500) assert.equal(c.getSnapshot().blocked, true)
      c.dispose()
    }
  })
  await check('성공 후 조회 실패는 완료 유지·재처리 금지·조회로만 복구·stale 처리 응답 거부', async () => {
    let loads = 0, calls = 0
    const c = createAdminController({ ...base(), list: async () => { loads++; if (loads === 2) throw Error('failed'); return page(loads >= 4 ? [] : [report]) }, submit: async () => { calls++ } }, 'reports', 'RECEIVED')
    await c.start(); await c.submit(job); assert.match(c.getSnapshot().notice, /완료/); assert.match(c.getSnapshot().error, /처리는 완료/)
    await c.submit(job); assert.equal(calls, 1); await c.refresh(); assert.equal(c.getSnapshot().blocked, true)
    await c.refresh(); assert.equal(c.getSnapshot().blocked, false); c.dispose()
  })
  await check('응답 유실은 조회 전 재전송 차단·정지는 조회 계약 부재로 잠금 유지', async () => {
    for (const kind of ['resolve', 'suspend']) {
      let calls = 0; const c = createAdminController({ ...base(), submit: async () => { calls++; throw new TypeError('lost') } }, kind === 'resolve' ? 'reports' : 'suspensions', kind === 'resolve' ? 'RECEIVED' : '')
      await c.start(); const draft = kind === 'resolve' ? job : { kind, id: 30, reportId: 10, days: 7, reason: '사유' }
      await c.submit(draft); await c.submit(draft); assert.equal(calls, 1); assert.equal(c.getSnapshot().notice, '')
      await c.refresh(); assert.equal(c.getSnapshot().blocked, kind === 'suspend'); c.dispose()
    }
  })
  await check('더 보기 커서·중복 요청/행 제거·실패 보존·빈 목록', async () => {
    const pending = deferred(); let calls = 0
    const c = createAdminController({ ...base(), list: async (_, __, cursor) => { calls++; return cursor ? pending.promise : page([report], '10') } }, 'reports', 'RECEIVED')
    await c.start(); const more = c.more(); await c.more(); assert.equal(calls, 2); pending.reject(Error('more')); await more
    assert.equal(c.getSnapshot().items.length, 1); assert.equal(c.getSnapshot().cursor, '10'); assert.ok(c.getSnapshot().moreError); c.dispose()
    const d = createAdminController({ ...base(), list: async () => page([]) }, 'reports', 'RECEIVED'); await d.start(); assert.equal(d.getSnapshot().loaded, true); assert.equal(d.getSnapshot().items.length, 0); d.dispose()
  })
  await check('계정/메뉴 전환·이탈: 늦은 서류·처리 응답 폐기·Abort·StrictMode 재활성', async () => {
    const pending = deferred(); let current = true, mutationSignal
    const c = createAdminController({ ...base(), submit: async (_, s) => { mutationSignal = s; return pending.promise } }, 'reports', 'RECEIVED', () => current)
    await c.start(); const running = c.submit(job); current = false; pending.resolve(); await running; assert.equal(c.getSnapshot().notice, ''); c.dispose(); assert.equal(mutationSignal.aborted, true)
    const image = deferred(), d = createAdminController({ ...base(), list: async () => page([verification]), document: () => image.promise }, 'verifications', 'PENDING')
    await d.start(); const photo = d.showDocument(11); d.dispose(); image.resolve(new Blob(['private'])); await photo; assert.equal(d.getSnapshot().document, null)
    d.resume(); await d.start(); assert.equal(d.getSnapshot().access, 'ready'); d.dispose()
  })
  await check('실제 API 오류는 모의 성공으로 전환하지 않음·토큰 변경 즉시 요청 차단', async () => {
    let current = true; globalThis.fetch = async () => envelope(account); const s = realAdmin('test', () => current); await s.account(signal)
    for (const status of [401, 403, 409, 500]) { globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'FAIL', message: 'fail' }), { status }); await assert.rejects(s.submit(job, signal), e => e.status === status) }
    current = false; let calls = 0; globalThis.fetch = async () => { calls++; return envelope(account) }; await assert.rejects(s.submit(job, signal)); assert.equal(calls, 0)
  })
  await check('관리자 경로 분리·권한 확인 중 화면·관리자 전용 스타일·공통 정지 입력', async () => {
    const { default: App } = await server.ssrLoadModule('/src/App.tsx')
    const { SuspensionFields } = await server.ssrLoadModule('/src/features/admin/AdminPage.tsx')
    for (const path of ['/admin', '/admin/reports', '/admin/verifications', '/admin/suspensions', '/admin/schools']) {
      const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(App)))
      assert.ok(html.includes('admin-sidebar')); assert.ok(html.includes('로그인 후 이용할 수 있어요.')); assert.ok(!html.includes('app-layout'))
    }
    const html = renderToStaticMarkup(createElement(SuspensionFields, { days: null, reason: '', disabled: false, onChange() {} }))
    for (const value of ['7일', '30일', '영구', '사용자에게 안내']) assert.ok(html.includes(value))
  })
  await check('실제 데이터 표·대상 내용 이스케이프·유형별 조치·심사/학교 입력 확인창', async () => {
    const { Reports, Verifications, Schools, JobDialog } = await server.ssrLoadModule('/src/features/admin/AdminPage.tsx')
    const c = createAdminController(base(), 'reports', 'RECEIVED'); await c.start()
    const html = renderToStaticMarkup(createElement(Reports, { rows: [{ ...report, targetType: 'USER' }], locked: false, onJob() {} }))
    assert.ok(html.includes('&lt;script&gt;unsafe&lt;/script&gt;')); assert.ok(!html.includes('>숨김</button>')); assert.ok(!html.includes('>삭제</button>'))
    const ready = c.getSnapshot()
    const v = renderToStaticMarkup(createElement(Verifications, { rows: [verification], locked: false, controller: c, state: ready, onJob() {} }))
    for (const text of ['서류 확인', '승인', '반려', 'demo@example.invalid']) assert.ok(v.includes(text))
    const s = renderToStaticMarkup(createElement(Schools, { rows: [school], locked: false, onJob() {} }))
    for (const text of ['학교 추가', '수정', '비활성화', 'test.example', '활성 학교 목록만']) assert.ok(s.includes(text))
    const dialog = renderToStaticMarkup(createElement(JobDialog, { initial: { kind: 'reject', id: 11, reason: '입력 보존' }, state: ready, controller: c, onClose() {} }))
    for (const text of ['취소', '반려 사유', '입력 보존', '서류 반려 확정']) assert.ok(dialog.includes(text))
    c.dispose()
  })
  process.env.VITE_ADMIN_MOCK = 'true'; mockServer = await createServer(config)
  const { createMockAdmin, adminExampleEnabled } = await mockServer.ssrLoadModule('/src/features/admin/mock.ts')
  const example = query => createMockAdmin(new URLSearchParams(`adminExample=true&${query}`))
  await check('개발 예시는 환경+쿼리 모두 필요·일반/비로그인 구분·실제 fetch 없음', async () => {
    assert.equal(adminExampleEnabled(new URLSearchParams()), false); assert.throws(() => createMockAdmin(new URLSearchParams()))
    let calls = 0; globalThis.fetch = async () => { calls++; assert.fail('mock fetch') }
    assert.equal((await example('adminViewer=user').account(signal)).role, 'USER'); await assert.rejects(example('adminViewer=guest').account(signal))
    const s = example(''); await s.account(signal); assert.ok((await s.list('reports', 'RECEIVED', undefined, signal)).hasNext); assert.equal(calls, 0)
  })
  await check('예시 전체 작업: 승인/반려·신고 4조치·정지·학교 추가/수정/중복/비활성', async () => {
    const s = example(''); await s.account(signal)
    await s.submit({ kind: 'approve', id: 1, reason: '' }, signal); await s.submit({ kind: 'reject', id: 2, reason: '확인 불가' }, signal)
    assert.equal((await s.list('verifications', 'PENDING', undefined, signal)).items.length, 1)
    await assert.rejects(s.document(1, signal))
    for (const [id, action] of [[50, 'NONE'], [47, 'HIDE'], [44, 'DELETE'], [41, 'SUSPEND']]) await s.submit({ kind: 'resolve', id, action, days: 30, reason: '사유' }, signal)
    assert.equal((await s.list('reports', 'RESOLVED', undefined, signal)).items.length, 4)
    await s.submit({ kind: 'suspend', id: 201, reportId: 49, days: null, reason: '사유' }, signal)
    const draft = { name: '신규 예시', campus: '분교', emailDomain: 'new.example', region: 'ASAN' }
    await s.submit({ kind: 'createSchool', id: 0, school: draft }, signal)
    await assert.rejects(s.submit({ kind: 'createSchool', id: 0, school: draft }, signal), e => e.code === 'SCHOOL_DOMAIN_DUPLICATED')
    const added = (await s.list('schools', '', undefined, signal)).items.find(r => r.name === draft.name)
    await s.submit({ kind: 'updateSchool', id: added.id, school: { ...draft, name: '수정 예시' } }, signal)
    assert.ok((await s.list('schools', '', undefined, signal)).items.some(r => r.name === '수정 예시'))
    await s.submit({ kind: 'deactivateSchool', id: added.id }, signal); assert.equal((await s.list('schools', '', undefined, signal)).items.length, 1)
  })
  console.log(`${passed} admin checks passed (intercepted API/controller/static render/mock; no real backend/browser).`)
} finally { globalThis.fetch = originalFetch; await mockServer?.close(); await server.close() }
