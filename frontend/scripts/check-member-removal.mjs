// 요청 가로채기·controller·정적 렌더 검증. 실제 인증/DB/브라우저 검증과 구분합니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const originalFetch = globalThis.fetch
const config = { server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' }
const server = await createServer(config)
let mockServer, passed = 0
const check = async (name, run) => { await run(); console.log(`PASS ${++passed} ${name}`) }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
try {
  const api = await server.ssrLoadModule('/src/features/applicants/api.ts')
  const model = await server.ssrLoadModule('/src/features/applicants/model.ts')
  const { createManagementController } = await server.ssrLoadModule('/src/features/applicants/controller.ts')
  const { mockDetail } = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const post = mockDetail('1', 'owner')
  const row = status => ({ id: 504, status, message: '지원', availableTime: '주말', createdAt: '2026-11-16T10:00:00', applicant: { id: 204, nickname: '지우', schoolName: null, major: null, grade: null, profileImageUrl: null } })
  const snapshot = status => ({ post: { ...post, roles: post.roles.map(r => ({ ...r })) }, roles: post.roles.map((r, i) => ({ postRoleId: r.id, roleName: r.roleName, headcount: r.headcount, filledCount: r.filledCount, applications: i === 1 ? [row(status)] : [] })) })
  const result = { applicationId: 504, status: 'LEFT', roleReopened: false, postReopened: false }
  const failure = (status, code) => new ApiError(status, { success: false, code, message: code })
  const envelope = data => new Response(JSON.stringify({ success: true, data }))
  const setup = (service, isCurrent) => { let state; const controller = createManagementController(service, value => { state = value }, isCurrent); return { controller, state: () => state } }
  await check('무토큰 차단·지원 ID의 PATCH leave·본문 없음·Bearer/AbortSignal·응답 검증', async () => {
    let calls = 0
    globalThis.fetch = async () => { calls++; assert.fail('no token') }
    for (const token of [undefined, '', ' ']) await assert.rejects(api.decideApplication(504, 'remove', null, { token }), e => e.code === 'AUTH_PENDING')
    assert.equal(calls, 0)
    const signal = new AbortController().signal
    globalThis.fetch = async (url, options) => {
      assert.equal(url, 'http://localhost:8080/api/applications/504/leave'); assert.equal(options.method, 'PATCH')
      assert.equal(options.body, undefined); assert.equal(options.signal, signal); assert.equal(options.headers.get('Authorization'), 'Bearer test')
      return envelope(result)
    }
    assert.deepEqual(await api.decideApplication(504, 'remove', null, { token: 'test', signal }), result)
    for (const value of [{ ...result, applicationId: 204 }, { ...result, status: 'ACCEPTED' }, { ...result, roleReopened: undefined }, { ...result, postReopened: undefined }]) {
      globalThis.fetch = async () => envelope(value)
      await assert.rejects(api.decideApplication(504, 'remove', null, { token: 'test' }), e => e.code === 'INVALID_RESPONSE')
    }
  })
  await check('ACCEPTED·팀장만 허용·마감 글도 허용·본인 나가기와 권한 구분', async () => {
    for (const status of ['PENDING', 'REJECTED', 'CANCELED', 'CLOSED', 'LEFT']) assert.ok(model.decisionBlock(snapshot(status), 504, 'remove'))
    const accepted = snapshot('ACCEPTED'); accepted.post.status = 'CLOSED'; accepted.post.deadline = '2020-01-01'
    assert.equal(model.decisionBlock(accepted, 504, 'remove'), '')
    accepted.post.isMine = false; assert.ok(model.decisionBlock(accepted, 504, 'remove'))
  })
  await check('확정 전 요청 없음·동일 tick 확정은 1회·인원과 모집 상태는 서버 재조회 사용', async () => {
    let calls = 0, loads = 0; const pending = deferred(), refresh = deferred()
    const c = setup({ load: async () => ++loads === 1 ? snapshot('ACCEPTED') : refresh.promise, decide: async () => { calls++; return pending.promise } })
    await c.controller.load(); assert.equal(calls, 0) // 확인창 취소는 controller를 호출하지 않습니다.
    const first = c.controller.decide(504, 'remove', null); await c.controller.decide(504, 'remove', null)
    assert.equal(calls, 1); pending.resolve(result); await Promise.resolve(); await Promise.resolve()
    assert.equal(c.state().snapshot.roles[1].applications[0].status, 'LEFT')
    assert.equal(c.state().snapshot.roles[1].filledCount, 1); assert.equal(c.state().needsRefresh, true)
    const serverSnapshot = snapshot('LEFT'); serverSnapshot.roles[1].filledCount = 0; serverSnapshot.post.roles[1].filledCount = 0
    refresh.resolve(serverSnapshot); await first
    assert.equal(loads, 2); assert.equal(c.state().snapshot.roles[1].filledCount, 0); assert.equal(c.state().needsRefresh, false)
    c.controller.dispose()
  })
  await check('처리 실패·권한/만료/없는 지원·이미 나감은 가짜 성공 없음·재전송 잠금', async () => {
    for (const [status, code] of [[500, 'INTERNAL_ERROR'], [401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'INVALID_APPLICATION_STATUS']]) {
      let calls = 0
      const c = setup({ load: async () => snapshot('ACCEPTED'), decide: async () => { calls++; throw failure(status, code) } })
      await c.controller.load(); await c.controller.decide(504, 'remove', null); await c.controller.decide(504, 'remove', null)
      assert.equal(calls, 1); assert.equal(c.state().notice, ''); assert.equal(c.state().error.code, code)
      if ([401, 403, 404].includes(status)) assert.equal(c.state().snapshot, null)
      else assert.equal(c.state().snapshot.roles[1].applications[0].status, 'ACCEPTED')
      c.controller.dispose()
    }
  })
  await check('처리 성공 후 재조회 실패·오래된 ACCEPTED는 완료 유지·조회로만 복구', async () => {
    let loads = 0, calls = 0
    const c = setup({ load: async () => { loads++; if (loads === 2) throw Error('refresh'); return snapshot(loads === 4 ? 'LEFT' : 'ACCEPTED') }, decide: async () => { calls++; return result } })
    await c.controller.load(); await c.controller.decide(504, 'remove', null)
    assert.match(c.state().notice, /내보내기/); assert.match(c.state().error.message, /처리는 완료/)
    await c.controller.decide(504, 'remove', null); assert.equal(calls, 1)
    await c.controller.load(); assert.equal(c.state().needsRefresh, true); assert.equal(c.state().snapshot.roles[1].applications[0].status, 'LEFT')
    await c.controller.load(); assert.equal(c.state().needsRefresh, false); c.controller.dispose()
  })
  await check('응답 유실은 기존 표시 유지·재요청 차단·조회로 LEFT 확인', async () => {
    let calls = 0, changed = false
    const c = setup({ load: async () => snapshot(changed ? 'LEFT' : 'ACCEPTED'), decide: async () => { calls++; changed = true; throw new TypeError('lost') } })
    await c.controller.load(); await c.controller.decide(504, 'remove', null); await c.controller.decide(504, 'remove', null)
    assert.equal(calls, 1); assert.equal(c.state().notice, ''); assert.equal(c.state().snapshot.roles[1].applications[0].status, 'ACCEPTED')
    await c.controller.load(); assert.equal(c.state().snapshot.roles[1].applications[0].status, 'LEFT'); assert.equal(c.state().needsRefresh, false); c.controller.dispose()
  })
  await check('계정·글 변경 후 늦은 처리/조회 무시·요청 Abort 전달', async () => {
    const pending = deferred(); let signal, current = true
    const c = setup({ load: async () => snapshot('ACCEPTED'), decide: async (_, __, ___, s) => { signal = s; return pending.promise } }, () => current)
    await c.controller.load(); const action = c.controller.decide(504, 'remove', null); current = false
    pending.resolve(result); await action; assert.equal(c.state().snapshot.roles[1].applications[0].status, 'ACCEPTED')
    c.controller.dispose(); assert.equal(signal.aborted, true)
    const loading = deferred(), d = setup({ load: () => loading.promise, decide: async () => result })
    const load = d.controller.load(); d.controller.dispose(); loading.resolve(snapshot('LEFT')); await load; assert.equal(d.state().snapshot, null)
  })
  await check('기존 확인창·대상 닉네임/역할·취소/확정·프로필·역할 탭 유지', async () => {
    const { DecisionDialog } = await server.ssrLoadModule('/src/features/applicants/DecisionDialog.tsx')
    const dialog = renderToStaticMarkup(createElement(DecisionDialog, { name: '지우', roleName: '디자인', decision: 'remove', reason: null, onCancel() {}, onConfirm() {} }))
    for (const text of ['지우', '디자인', '취소', '내보내기 확인', 'dialog']) assert.ok(dialog.includes(text))
    const { ApplicantsView } = await server.ssrLoadModule('/src/features/applicants/ApplicantsView.tsx')
    const c = setup({ load: async () => snapshot('ACCEPTED'), decide: async () => result }); await c.controller.load()
    const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(ApplicantsView, { snapshot: c.state().snapshot, state: c.state(), reason: null, onReason() {}, onAction() {}, onRefresh() {}, search: '' })))
    for (const text of ['내보내기', '/users/204', '역할별 지원자 선택', '목록·모집 현황 갱신']) assert.ok(html.includes(text))
    c.controller.dispose()
  })
  process.env.VITE_POSTS_MOCK = 'true'; mockServer = await createServer(config)
  const { createMockManagement } = await mockServer.ssrLoadModule('/src/features/applicants/mock.ts')
  await check('명시적 개발 예시: 성공·재모집/마감 유지·응답 유실·이미 나감·재조회 실패', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; assert.fail('mock fetch') }
    for (const scenario of ['normal', 'reopen', 'expiredClosed']) {
      const service = createMockManagement('1', new URLSearchParams(`manage=${scenario}`)), signal = new AbortController().signal
      const before = await service.load(signal); const changed = await service.decide(504, 'remove', null); const after = await service.load(signal)
      assert.equal(changed.status, 'LEFT'); assert.equal(after.roles[1].filledCount, before.roles[1].filledCount - 1)
      assert.equal(after.post.status, scenario === 'expiredClosed' ? 'CLOSED' : 'OPEN')
      assert.equal(after.post.roles[1].status, scenario === 'expiredClosed' ? 'CLOSED' : 'OPEN')
    }
    for (const outcome of ['responseLost', 'alreadyLeft', 'refreshFailure']) {
      const service = createMockManagement('1', new URLSearchParams(`result=${outcome}`)), signal = new AbortController().signal
      await service.load(signal)
      if (outcome === 'refreshFailure') { await service.decide(504, 'remove', null); await assert.rejects(service.load(signal)) }
      else { await assert.rejects(service.decide(504, 'remove', null)); assert.equal((await service.load(signal)).roles[1].applications[0].status, 'LEFT') }
    }
    assert.equal(calls, 0)
  })
  await check('0/1 A 수락→내보내기: 역할 OPEN 복원·기존 B CLOSED 유지·잠금 해제·추가 요청 없음', async () => {
    const service = createMockManagement('1', new URLSearchParams()), calls = []
    const c = setup({ ...service, decide: (...args) => { calls.push(args.slice(0, 2)); return service.decide(...args) } })
    await c.controller.load(); await c.controller.decide(501, 'accept', null)
    let s = c.state().snapshot
    assert.equal(s.post.status, 'OPEN'); assert.equal(s.post.roles[0].status, 'CLOSED')
    assert.equal(s.roles[0].filledCount, 1); assert.equal(model.findApplication(s, 502).application.status, 'CLOSED')
    await c.controller.decide(501, 'remove', null); s = c.state().snapshot
    assert.equal(s.post.status, 'OPEN'); assert.equal(s.post.roles[0].status, 'OPEN'); assert.equal(s.roles[0].filledCount, 0)
    assert.equal(model.findApplication(s, 502).application.status, 'CLOSED')
    assert.equal(c.state().busy, false); assert.equal(c.state().loading, false); assert.equal(c.state().needsRefresh, false)
    assert.match(model.decisionBlock(s, 502, 'accept'), /종료된 지원/)
    await c.controller.decide(502, 'accept', null); assert.deepEqual(calls, [[501, 'accept'], [501, 'remove']])
    const { ApplicantsView } = await server.ssrLoadModule('/src/features/applicants/ApplicantsView.tsx')
    const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(ApplicantsView, { snapshot: s, state: c.state(), reason: null, onReason() {}, onAction() {}, onRefresh() {}, search: '' })))
    assert.ok(html.includes('이 지원은 다시 수락할 수 없어요.')); c.controller.dispose()
  })
  await check('A 수락→A 내보내기→재모집 후 새 PENDING B 수락 연속 성공', async () => {
    const service = createMockManagement('1', new URLSearchParams('manage=replacement')), calls = []
    const c = setup({ ...service, decide: (...args) => { calls.push(args.slice(0, 2)); return service.decide(...args) } })
    await c.controller.load(); await c.controller.decide(501, 'accept', null); await c.controller.decide(501, 'remove', null)
    assert.equal(model.findApplication(c.state().snapshot, 502).application.status, 'CLOSED')
    assert.equal(model.findApplication(c.state().snapshot, 508).application.status, 'PENDING')
    assert.equal(model.decisionBlock(c.state().snapshot, 508, 'accept'), '')
    assert.equal(c.state().busy || c.state().loading || c.state().needsRefresh, false)
    await c.controller.decide(508, 'accept', null)
    assert.equal(model.findApplication(c.state().snapshot, 508).application.status, 'ACCEPTED')
    assert.equal(c.state().snapshot.roles[0].filledCount, 1)
    assert.deepEqual(calls, [[501, 'accept'], [501, 'remove'], [508, 'accept']]); c.controller.dispose()
  })
  await check('실제 API 경로: OPEN/PENDING B는 재조회 후 수락 가능·마감 역할은 빈자리에도 차단', async () => {
    // fetch를 가로채 실제 API 모듈과 controller를 함께 검증합니다. 실서버 검증은 아닙니다.
    let current = snapshot('PENDING'); current.roles[1].filledCount = 0; current.post.roles[1].filledCount = 0
    const b = { ...row('PENDING'), id: 505, applicant: { ...row('PENDING').applicant, id: 205 } }
    current.roles[1].applications.push(b)
    const methods = []
    globalThis.fetch = async (url, options) => {
      const path = new URL(url).pathname
      if (options.method === 'PATCH') {
        methods.push(path)
        if (path.endsWith('/504/accept')) {
          const accepted = { applicationId: 504, status: 'ACCEPTED', roleClosed: false, postClosed: false }
          current = model.applyDecision(current, accepted); return envelope(accepted)
        }
        if (path.endsWith('/504/leave')) {
          current = structuredClone(current); current.roles[1].applications[0].status = 'LEFT'
          current.roles[1].filledCount = 0; current.post.roles[1].filledCount = 0
          return envelope(result)
        }
        assert.equal(path, '/api/applications/505/accept')
        const accepted = { applicationId: 505, status: 'ACCEPTED', roleClosed: false, postClosed: false }
        current = model.applyDecision(current, accepted); return envelope(accepted)
      }
      return envelope(path.endsWith('/applications') ? current.roles : current.post)
    }
    const options = { token: 'test' }
    const c = setup({ load: signal => api.loadManagement('1', { ...options, signal }), decide: (id, decision, reason, signal) => api.decideApplication(id, decision, reason, { ...options, signal }) })
    await c.controller.load(); await c.controller.decide(504, 'accept', null); await c.controller.decide(504, 'remove', null)
    assert.equal(model.decisionBlock(c.state().snapshot, 505, 'accept'), '')
    await c.controller.decide(505, 'accept', null)
    assert.equal(model.findApplication(c.state().snapshot, 505).application.status, 'ACCEPTED')
    assert.deepEqual(methods, ['/api/applications/504/accept', '/api/applications/504/leave', '/api/applications/505/accept'])
    current = structuredClone(current); current.post.status = 'CLOSED'; current.post.roles[1].status = 'CLOSED'
    current.roles[1].applications[1].status = 'PENDING'; current.roles[1].filledCount = 0; current.post.roles[1].filledCount = 0
    c.controller.dispose()
    const d = setup({ load: signal => api.loadManagement('1', { ...options, signal }), decide: (id, decision, reason, signal) => api.decideApplication(id, decision, reason, { ...options, signal }) })
    await d.controller.load(); assert.equal(model.findApplication(d.state().snapshot, 505).application.status, 'PENDING')
    assert.ok(model.decisionBlock(d.state().snapshot, 505, 'accept'))
    await d.controller.decide(505, 'accept', null); assert.equal(methods.length, 3); d.controller.dispose()
  })
  await check('마감일 경과: 내보내기 후 빈자리에도 마감 유지·PENDING 수락 요청 없음·이유 표시', async () => {
    const service = createMockManagement('1', new URLSearchParams('manage=expiredClosed')); let calls = 0
    const c = setup({ ...service, decide: (...args) => { calls++; return service.decide(...args) } })
    await c.controller.load(); await c.controller.decide(504, 'remove', null)
    const s = c.state().snapshot
    assert.equal(s.post.status, 'CLOSED'); assert.equal(s.post.roles[1].status, 'CLOSED')
    assert.equal(s.roles[1].filledCount, 1); assert.equal(model.findApplication(s, 501).application.status, 'PENDING')
    assert.equal(c.state().busy || c.state().loading || c.state().needsRefresh, false)
    await c.controller.decide(501, 'accept', null); assert.equal(calls, 1)
    const { ApplicantsView } = await server.ssrLoadModule('/src/features/applicants/ApplicantsView.tsx')
    const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(ApplicantsView, { snapshot: s, state: c.state(), reason: null, onReason() {}, onAction() {}, onRefresh() {}, search: '' })))
    assert.ok(html.includes('서버가 마감 상태를 유지합니다.')); c.controller.dispose()
  })
  console.log(`${passed} member removal checks passed (intercepted API/controller/static render; no real server/DB/browser).`)
} finally { globalThis.fetch = originalFetch; await mockServer?.close(); await server.close() }
