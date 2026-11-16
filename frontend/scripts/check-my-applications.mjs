// fetch 가로채기·상태 제어·정적 렌더 검증. 실제 JWT/DB/브라우저 통합 검증은 아닙니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { readFile } from 'node:fs/promises'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let mockServer, passed = 0
async function check(name, run) { await run(); console.log(`PASS ${++passed} ${name}`) }
const deferred = () => { let resolve, reject; const promise = new Promise((r, j) => { resolve = r; reject = j }); return { promise, resolve, reject } }
const row = (id = 107, status = 'PENDING', postId = 5) => ({ id, status, roleName: '기획', post: { id: postId, title: '광고 마케팅 공모전 기획팀', deleted: false }, createdAt: '2026-11-12T13:00:00', decidedAt: null })
const page = (items = [row()], nextCursor = null) => ({ items, nextCursor, hasNext: nextCursor !== null })
const json = data => new Response(JSON.stringify({ success: true, data }), { headers: { 'Content-Type': 'application/json' } })
const token = { token: 'test-only-token' }
try {
  const api = await server.ssrLoadModule('/src/features/my-applications/api.ts')
  const model = await server.ssrLoadModule('/src/features/my-applications/model.ts')
  const { createMyApplicationsController, initialListState } = await server.ssrLoadModule('/src/features/my-applications/controller.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const realSource = await server.ssrLoadModule('/src/features/my-applications/source.ts')
  const error = (status, code) => new ApiError(status, { success: false, code, message: code })
  function harness(service, status = '') {
    let state = initialListState(), updates = 0
    const manager = createMyApplicationsController(service, status, value => { state = value; updates++ })
    return { manager, get state() { return state }, get updates() { return updates } }
  }
  await check('기본 전체·6개 상태 URL 복원·잘못된 상태 제외·필터 변경 시 커서 삭제', () => {
    assert.equal(model.readFilter(new URLSearchParams()), '')
    for (const status of Object.keys(model.statusLabels)) assert.equal(model.readFilter(new URLSearchParams({ status })), status)
    assert.equal(model.readFilter(new URLSearchParams('status=NO_SUCH')), '')
    const p = model.filterParams(new URLSearchParams('status=PENDING&cursor=999&size=3&example=true'), 'ACCEPTED')
    assert.equal(p.get('status'), 'ACCEPTED'); assert.equal(p.has('cursor'), false); assert.equal(p.has('size'), false); assert.equal(p.get('example'), 'true')
    assert.equal(model.filterParams(p, '').has('status'), false)
  })
  await check('실제 GET 전체/필터·size20·지원 ID 커서·Bearer/AbortSignal', async () => {
    const calls = [], signal = new AbortController().signal
    globalThis.fetch = async (url, init) => { calls.push({ url, init }); return json(page()) }
    await api.getMyApplications('', undefined, { ...token, signal })
    assert.equal(calls[0].url, 'http://localhost:8080/api/users/me/applications?size=20')
    assert.equal(calls[0].init.headers.get('Authorization'), 'Bearer test-only-token'); assert.equal(calls[0].init.signal, signal)
    await api.getMyApplications('PENDING', '9223372036854775807', token)
    const query = new URL(calls[1].url).searchParams
    assert.equal(query.get('status'), 'PENDING'); assert.equal(query.get('cursor'), '9223372036854775807')
    assert.equal(query.has('sort'), false)
  })
  await check('토큰 없음/빈값/공백은 조회·취소·나가기 fetch0·실제 모드 예시 쿼리 무시', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('must not fetch') }
    for (const value of [undefined, '', '  ']) {
      await assert.rejects(api.getMyApplications('', undefined, { token: value }), e => e.code === 'AUTH_PENDING')
      for (const action of ['cancel', 'leave']) await assert.rejects(api.actOnApplication(107, action, { token: value }), e => e.code === 'AUTH_PENDING')
    }
    const service = realSource.myApplicationsService(new URLSearchParams('example=true&viewer=alice'))
    await assert.rejects(service.load('', undefined, new AbortController().signal), e => e.code === 'AUTH_PENDING')
    assert.equal(calls, 0)
  })
  await check('PATCH는 지원 ID·본문 없음·cancel/leave 응답 계약 분리', async () => {
    for (const action of ['cancel', 'leave']) {
      globalThis.fetch = async (url, init) => {
        assert.equal(url, `http://localhost:8080/api/applications/107/${action}`); assert.equal(init.method, 'PATCH'); assert.equal(init.body, undefined)
        return json({ applicationId: 107, status: action === 'cancel' ? 'CANCELED' : 'LEFT', ...(action === 'leave' ? { roleReopened: false, postReopened: true } : {}) })
      }
      const result = await api.actOnApplication(107, action, token); assert.equal(result.applicationId, 107)
    }
    globalThis.fetch = async () => json({ applicationId: 5, status: 'CANCELED' })
    await assert.rejects(api.actOnApplication(107, 'cancel', token), e => e.code === 'INVALID_RESPONSE')
    globalThis.fetch = async () => json({ applicationId: 107, status: 'LEFT' })
    await assert.rejects(api.actOnApplication(107, 'leave', token), e => e.code === 'INVALID_RESPONSE')
  })
  await check('서버 응답·잘못된 커서/중복 ID/누락값 차단·알 수 없는 지원 상태 보존', async () => {
    assert.equal(api.validatePage(page([row(1, 'NEW_STATUS')])).items[0].status, 'NEW_STATUS')
    assert.match(model.statusLabel('NEW_STATUS'), /알 수 없는/)
    for (const value of [page([], '10'), page([row()], 'same'), page([row()], '107'), page([row(), row()]), page([{ ...row(), post: { id: 5, deleted: false, title: null } }]), page([{ ...row(), decidedAt: undefined }])]) {
      const cursor = value.nextCursor === '107' ? '107' : undefined
      assert.throws(() => api.validatePage(value, cursor))
    }
    globalThis.fetch = async () => json(page())
    await assert.rejects(api.getMyApplications('NO_SUCH', undefined, token), e => e.code === 'INVALID_INPUT')
    for (const cursor of ['bad', '0', '9223372036854775808']) await assert.rejects(api.getMyApplications('', cursor, token), e => e.code === 'INVALID_INPUT')
  })
  await check('401/403/404/409/500·네트워크 실패를 mock 성공으로 대체하지 않음', async () => {
    for (const [status, code] of [[401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'INVALID_APPLICATION_STATUS'], [500, 'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: code }), { status })
      await assert.rejects(api.getMyApplications('', undefined, token), e => e.code === code)
      await assert.rejects(api.actOnApplication(107, 'cancel', token), e => e.code === code)
    }
    globalThis.fetch = async () => { throw Error('network') }; await assert.rejects(api.getMyApplications('', undefined, token))
  })
  await check('최초 로딩·빈 목록·최초 실패와 재시도 복구', async () => {
    let fail = true
    const h = harness({ load: async () => { if (fail) throw Error('load'); return page([]) }, act: async () => {} })
    assert.equal(h.state.loading, true); await h.manager.load(); assert.equal(h.state.loaded, false); assert.ok(h.state.error)
    fail = false; await h.manager.load(); assert.equal(h.state.loaded, true); assert.equal(h.state.error, null); assert.deepEqual(h.state.items, [])
  })
  await check('필터별 첫 페이지·더 보기 커서 전달·같은 tick 중복 요청·중복 항목 제거', async () => {
    const wait = deferred(), calls = []
    const h = harness({ load: async (filter, cursor) => { calls.push([filter, cursor]); return cursor ? wait.promise : page([row(108), row(107)], '107') }, act: async () => {} }, 'PENDING')
    await h.manager.load(); const one = h.manager.more(); await h.manager.more(); assert.equal(calls.length, 2)
    wait.resolve(page([row(107), row(106)])); await one
    assert.deepEqual(calls, [['PENDING', undefined], ['PENDING', '107']]); assert.deepEqual(h.state.items.map(r => r.id), [108, 107, 106]); assert.equal(h.state.hasNext, false)
  })
  await check('더 보기 실패 시 기존 목록/커서 유지·재시도', async () => {
    let fail = true
    const h = harness({ load: async (_filter, cursor) => { if (cursor && fail) throw Error('more'); return cursor ? page([row(106)]) : page([row()], '107') }, act: async () => {} })
    await h.manager.load(); await h.manager.more(); assert.equal(h.state.items.length, 1); assert.equal(h.state.nextCursor, '107'); assert.ok(h.state.moreError)
    fail = false; await h.manager.more(); assert.equal(h.state.items.length, 2); assert.equal(h.state.moreError, '')
  })
  await check('오래된 최초/추가 응답이 재조회 결과를 덮어쓰지 않음', async () => {
    const old = deferred(); let calls = 0
    const h = harness({ load: () => ++calls === 1 ? old.promise : Promise.resolve(page([row(900)])), act: async () => {} })
    const first = h.manager.load(); await h.manager.load(); old.resolve(page([row(1)])); await first
    assert.equal(h.state.items[0].id, 900)
    const more = deferred(); let refresh = false
    const b = harness({ load: async (_f, cursor) => cursor ? more.promise : page([row(refresh ? 900 : 108)], refresh ? null : '108'), act: async () => {} })
    await b.manager.load(); const pending = b.manager.more(); refresh = true; await b.manager.load(); more.resolve(page([row(1)])); await pending
    assert.deepEqual(b.state.items.map(r => r.id), [900])
  })
  await check('PENDING만 취소·ACCEPTED만 나가기·나머지/알 수 없는 상태 무요청', async () => {
    let calls = 0
    const h = harness({ load: async () => page(Object.keys(model.statusLabels).map((s, i) => row(200 - i, s)).concat(row(100, 'NEW_STATUS'))), act: async () => { calls++; throw Error('request') } })
    await h.manager.load()
    for (const item of h.state.items) {
      assert.equal(model.actionFor(item), item.status === 'PENDING' ? 'cancel' : item.status === 'ACCEPTED' ? 'leave' : null)
      if (item.status !== 'PENDING') await h.manager.act(item.id, 'cancel')
      if (item.status !== 'ACCEPTED') await h.manager.act(item.id, 'leave')
    }
    assert.equal(calls, 0)
  })
  await check('취소 같은 tick 1회·서버 재조회·CANCELED 유지·지원 ID와 글 ID 분리', async () => {
    let canceled = false, calls = 0; const wait = deferred()
    const h = harness({ load: async () => page([row(107, canceled ? 'CANCELED' : 'PENDING', 5)]), act: async (id, action) => { calls++; assert.equal(id, 107); assert.equal(action, 'cancel'); await wait.promise; canceled = true; return { applicationId: id, status: 'CANCELED' } } })
    await h.manager.load(); const first = h.manager.act(107, 'cancel'); await h.manager.act(107, 'cancel'); assert.equal(calls, 1)
    wait.resolve(); await first; assert.equal(h.state.items[0].status, 'CANCELED'); assert.equal(h.state.needsRefresh, false); assert.match(h.state.notice, /다시 지원할 수 없/)
    await h.manager.act(107, 'cancel'); assert.equal(calls, 1)
  })
  await check('PENDING 필터 취소 후 서버 목록에서 제외·전체/취소 목록 정책은 서버 기준', async () => {
    let canceled = false
    const h = harness({ load: async filter => { assert.equal(filter, 'PENDING'); return canceled ? page([]) : page() }, act: async () => { canceled = true; return { applicationId: 107, status: 'CANCELED' } } }, 'PENDING')
    await h.manager.load(); await h.manager.act(107, 'cancel'); assert.deepEqual(h.state.items, []); assert.ok(h.state.notice)
  })
  await check('성공 후 재조회 실패/지연 PENDING: 완료 상태 유지·재처리 금지·조회만 재시도', async () => {
    for (const stale of [false, true]) {
      let reads = 0, actions = 0
      const h = harness({ load: async () => { reads++; if (reads === 2 && !stale) throw Error('refresh'); return page([row(107, reads >= 3 ? 'CANCELED' : 'PENDING')]) }, act: async () => { actions++; return { applicationId: 107, status: 'CANCELED' } } })
      await h.manager.load(); await h.manager.act(107, 'cancel')
      assert.equal(h.state.items[0].status, 'CANCELED'); assert.ok(h.state.notice); assert.ok(h.state.error); assert.equal(h.state.needsRefresh, true)
      await h.manager.act(107, 'cancel'); assert.equal(actions, 1)
      await h.manager.load(); assert.equal(h.state.error, null); assert.equal(h.state.needsRefresh, false)
    }
  })
  await check('팀 나가기 성공은 LEFT·연락처 안내 제거·취소 API 사용 안 함', async () => {
    let left = false
    const h = harness({ load: async () => page([row(108, left ? 'LEFT' : 'ACCEPTED')]), act: async (id, action) => { assert.equal(action, 'leave'); left = true; return { applicationId: id, status: 'LEFT', roleReopened: false, postReopened: false } } })
    await h.manager.load(); await h.manager.act(108, 'leave'); assert.equal(h.state.items[0].status, 'LEFT'); assert.match(h.state.notice, /연락 수단 접근 권한이 종료/)
  })
  await check('취소 전 수락됨 409·서버/네트워크 불명확: 성공 안내 없음·재조회 전 잠금', async () => {
    for (const failure of [error(409, 'INVALID_APPLICATION_STATUS'), error(500, 'INTERNAL_ERROR'), Error('network')]) {
      let reads = 0, actions = 0
      const h = harness({ load: async () => page([row(107, ++reads > 1 ? 'ACCEPTED' : 'PENDING')]), act: async () => { actions++; throw failure } })
      await h.manager.load(); await h.manager.act(107, 'cancel'); assert.equal(h.state.notice, ''); assert.equal(h.state.needsRefresh, true)
      await h.manager.act(107, 'cancel'); assert.equal(actions, 1)
      await h.manager.load(); assert.equal(h.state.items[0].status, 'ACCEPTED'); assert.equal(h.state.needsRefresh, false)
    }
  })
  await check('인증/권한 만료는 목록 제거·이전 사용자 요청 abort/늦은 조회/처리 무시', async () => {
    const h = harness({ load: async () => page(), act: async () => { throw error(401, 'UNAUTHORIZED') } })
    await h.manager.load(); await h.manager.act(107, 'cancel'); assert.deepEqual(h.state.items, []); assert.equal(h.state.hasNext, false)
    for (const acting of [false, true]) {
      const wait = deferred(); let signal
      const a = harness({ load: async (_f, _c, s) => { signal = s; return acting ? page() : wait.promise }, act: async (_id, _action, s) => { signal = s; return wait.promise } })
      if (acting) await a.manager.load()
      const pending = acting ? a.manager.act(107, 'cancel') : a.manager.load(), updates = a.updates
      a.manager.dispose(); assert.equal(signal.aborted, true)
      const b = harness({ load: async () => page([row(900)]), act: async () => {} }); await b.manager.load()
      wait.resolve(acting ? { applicationId: 107, status: 'CANCELED' } : page([row(1)])); await pending
      assert.equal(a.updates, updates); assert.equal(b.state.items[0].id, 900)
    }
  })
  await check('정적 목록: 상태와 모집글 상태 분리·삭제 링크 금지·거절 사유/연락처 조작 없음', async () => {
    const { MyApplicationsList } = await server.ssrLoadModule('/src/features/my-applications/MyApplicationsList.tsx')
    const render = items => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(MyApplicationsList, { state: { ...initialListState(), loading: false, loaded: true, items }, mock: false, onAction() {} })))
    const accepted = render([row(108, 'ACCEPTED', 12)])
    assert.ok(accepted.includes('수락')); assert.ok(accepted.includes('href="/posts/12"')); assert.ok(accepted.includes('상세에서 연락 수단 확인'))
    assert.ok(!accepted.includes('지원 취소')); assert.ok(!accepted.includes('https://open.kakao'))
    const deleted = render([{ ...row(105, 'REJECTED'), post: { id: 3, title: '서버가 보내도 숨김', deleted: true } }])
    assert.ok(deleted.includes('삭제된 모집글')); assert.ok(!deleted.includes('href=')); assert.ok(!deleted.includes('서버가 보내도 숨김')); assert.ok(!deleted.includes('거절 사유'))
    const unknown = render([row(1, '<NEW>')]); assert.ok(unknown.includes('&lt;NEW&gt;')); assert.ok(!unknown.includes('>지원 취소</button>')); assert.ok(!unknown.includes('>팀 나가기</button>'))
    const left = render([row(1, 'LEFT')]); assert.ok(!left.includes('연락 수단 확인'))
  })
  await check('확인창: 취소 재지원 불가·팀 나가기 연락처 권한 종료·기존 라우트 레이아웃', async () => {
    const { ApplicationActionDialog } = await server.ssrLoadModule('/src/features/my-applications/ApplicationActionDialog.tsx')
    for (const action of ['cancel', 'leave']) {
      const html = renderToStaticMarkup(createElement(ApplicationActionDialog, { item: row(), action, onClose() {}, onConfirm() {} }))
      assert.ok(html.includes('같은 모집글에 다시 지원할 수 없습니다'))
      if (action === 'leave') assert.ok(html.includes('연락 수단을 더 이상'))
    }
    const { default: App } = await server.ssrLoadModule('/src/App.tsx')
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/me/applications?status=ACCEPTED'] }, createElement(App)))
    assert.ok(html.includes('class="posts-layout"')); assert.ok(html.includes('내 지원 내역')); assert.ok(html.includes('aria-pressed="true"'))
    const source = await readFile('src/pages/MyApplicationsPage.tsx', 'utf8')
    assert.ok(source.includes('key={scope}')); assert.ok(source.includes("window.addEventListener('focus'")); assert.ok(source.includes("document.addEventListener('visibilitychange'"))
  })
  process.env.VITE_POSTS_MOCK = 'true'
  mockServer = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
  const mock = await mockServer.ssrLoadModule('/src/features/my-applications/source.ts')
  const signal = () => new AbortController().signal
  let caseId = 0
  const params = extra => new URLSearchParams({ example: 'true', reset: String(++caseId), ...extra })
  await check('명시적 예시 모드·structuredClone 없이 전체 상태/필터/페이지·사용자/로그아웃 격리', async () => {
    const original = globalThis.structuredClone; globalThis.structuredClone = undefined
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('must not fetch') }
    try {
      assert.equal(mock.exampleMode(new URLSearchParams()), false)
      const q = params(), s = mock.myApplicationsService(q), first = await s.load('', undefined, signal()), second = await s.load('', first.nextCursor, signal())
      assert.equal(first.items.length, 4); assert.equal(first.hasNext, true); assert.equal(second.hasNext, false)
      assert.deepEqual(new Set([...first.items, ...second.items].map(r => r.status)), new Set(Object.keys(model.statusLabels)))
      for (const status of Object.keys(model.statusLabels)) assert.ok((await s.load(status, undefined, signal())).items.every(row => row.status === status))
      const b = mock.myApplicationsService(params({ viewer: 'bob' })); assert.equal((await b.load('', undefined, signal())).items[0].id, 900)
      await assert.rejects(mock.myApplicationsService(params({ viewer: 'guest' })).load('', undefined, signal()), e => e.code === 'UNAUTHORIZED')
      assert.equal(calls, 0)
    } finally { globalThis.structuredClone = original }
  })
  await check('예시 취소·팀 나가기·필터 이동 후 유지·후속 조회 실패 후 조회만 복구', async () => {
    for (const action of ['cancel', 'leave']) {
      const q = params(), s = mock.myApplicationsService(q), id = action === 'cancel' ? 107 : 108
      await s.act(id, action, signal()); const filtered = mock.myApplicationsService(new URLSearchParams(`${q}&status=${action === 'cancel' ? 'CANCELED' : 'LEFT'}`))
      assert.ok((await filtered.load(action === 'cancel' ? 'CANCELED' : 'LEFT', undefined, signal())).items.some(r => r.id === id))
    }
    const s = mock.myApplicationsService(params({ result: 'refreshFailure' }))
    await s.act(107, 'cancel', signal()); await assert.rejects(s.load('', undefined, signal()))
    assert.equal((await s.load('', undefined, signal())).items.find(r => r.id === 107).status, 'CANCELED')
  })
  await check('예시 빈 목록/조회·더보기 실패/취소 거절·불명확·마감/삭제/숨김', async () => {
    assert.deepEqual((await mock.myApplicationsService(params({ list: 'empty' })).load('', undefined, signal())).items, [])
    await assert.rejects(mock.myApplicationsService(params({ list: 'error' })).load('', undefined, signal()))
    const more = mock.myApplicationsService(params({ list: 'moreError' })), first = await more.load('', undefined, signal()); await assert.rejects(more.load('', first.nextCursor, signal()))
    for (const result of ['failure', 'network', 'changed', 'expired', 'forbidden', 'noAuth']) {
      const s = mock.myApplicationsService(params({ result })); await assert.rejects(s.act(107, 'cancel', signal()))
      if (result === 'changed') assert.equal((await s.load('', undefined, signal())).items.find(r => r.id === 107).status, 'ACCEPTED')
    }
    const restricted = await mock.myApplicationsService(params({ list: 'restrictedPosts' })).load('', undefined, signal())
    assert.equal(restricted.items[0].post.id, 12); assert.equal(restricted.items[0].status, 'ACCEPTED'); assert.equal(restricted.items[1].post.deleted, true); assert.equal(restricted.items[2].post.id, 999)
  })
  console.log(`${passed} my application checks passed (intercepted API/controller/static render; no real API/DB/browser).`)
} finally { globalThis.fetch = originalFetch; await mockServer?.close(); await server.close() }
