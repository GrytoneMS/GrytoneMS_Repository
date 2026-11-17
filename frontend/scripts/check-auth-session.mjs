// 요청 가로채기·세션 상태·정적 렌더 검증. 실서버/JWT/브라우저 통합 검증과 구분합니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let passed = 0
const check = async (name, run) => { await run(); console.log(`PASS ${++passed} ${name}`) }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
const json = data => new Response(JSON.stringify({ success: true, data }))
try {
  const { createAuthStore, loadAccount } = await server.ssrLoadModule('/src/features/auth/session.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { getPost, getPosts } = await server.ssrLoadModule('/src/features/posts/api.ts')
  const { createPost } = await server.ssrLoadModule('/src/features/posts/create-api.ts')
  const { createPostSubmitter } = await server.ssrLoadModule('/src/features/posts/create-submit.ts')
  const model = await server.ssrLoadModule('/src/features/posts/create-model.ts')
  const edit = await server.ssrLoadModule('/src/features/posts/edit-model.ts')
  const { applyToPost } = await server.ssrLoadModule('/src/features/posts/application-api.ts')
  const { refreshAppliedPost } = await server.ssrLoadModule('/src/features/posts/application-submit.ts')
  const { closePost, getPostContact } = await server.ssrLoadModule('/src/features/posts/post-actions-api.ts')
  const { getMyPosts } = await server.ssrLoadModule('/src/features/my-posts/api.ts')
  const { getMyApplications, actOnApplication } = await server.ssrLoadModule('/src/features/my-applications/api.ts')
  const { mockDetail } = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const user = { id: 7, nickname: '예시', status: 'VERIFIED', role: 'USER' }
  const fault = (status, code) => new ApiError(status, { success: false, code, message: 'test' })
  const ready = async (account = user) => { const store = createAuthStore(() => 'synthetic-test-only', async () => account); await store.refresh(); return store }
  const empty = { items: [], hasNext: false, nextCursor: null }
  const draft = () => ({ ...model.initialPostDraft(), title: '인증 연결 확인', category: 'PROJECT', description: '함께 프로젝트를 만들어갈 대학생 팀원을 모집합니다.', periodStart: '2099-01-02', periodEnd: '2099-02-02', deadline: '2099-01-01', meetingType: 'ONLINE', roles: [{ ...model.emptyRole(0), roleCode: 'DEV', roleName: '개발' }], contactLink: 'https://example.com/contact' })
  await check('비로그인 공개 목록/상세는 Bearer 없이 조회·보호 요청은 fetch 0', async () => {
    let calls = 0
    globalThis.fetch = async (url, options) => { calls++; assert.equal(options.headers.has('Authorization'), false); return json(url.includes('?') ? empty : mockDetail('1', 'other')) }
    const store = createAuthStore(() => null); await store.refresh()
    assert.equal(store.getSnapshot().status, 'anonymous')
    await getPosts(); await getPost('1'); assert.equal(calls, 2)
    await assert.rejects(store.execute(() => createPost(draft())), e => e.code === 'UNAUTHORIZED'); assert.equal(calls, 2); store.dispose()
  })
  await check('토큰만 있고 사용자 확인 중이면 보호 요청 차단·실제 ID/상태/권한 응답 사용', async () => {
    const pending = deferred(), store = createAuthStore(() => 'synthetic', () => pending.promise)
    const loading = store.refresh(); let calls = 0
    assert.equal(store.getSnapshot().status, 'checking'); assert.equal(store.getSnapshot().session, undefined)
    await assert.rejects(store.execute(async () => { calls++ }), e => e.code === 'AUTH_CHECKING')
    pending.resolve({ ...user, id: 42, status: 'UNVERIFIED', role: 'ADMIN' }); await loading
    assert.equal(store.requireSession().userId, '42'); assert.equal(store.requireSession().account.role, 'ADMIN')
    await assert.rejects(store.execute(async () => { calls++ }, true), e => e.code === 'UNVERIFIED'); assert.equal(calls, 0); store.dispose()
  })
  await check('/api/users/me Bearer·no-store·잘못된/탈퇴 계정 응답 처리', async () => {
    globalThis.fetch = async (url, options) => { assert.equal(url, 'http://localhost:8080/api/users/me'); assert.ok(options.headers.get('Authorization')); assert.equal(options.cache, 'no-store'); return json(user) }
    assert.equal((await loadAccount('synthetic', new AbortController().signal)).id, 7)
    for (const value of [{ ...user, id: null }, { ...user, status: 'UNKNOWN' }, { ...user, role: 'UNKNOWN' }]) { globalThis.fetch = async () => json(value); await assert.rejects(loadAccount('synthetic', new AbortController().signal), e => e.code === 'INVALID_RESPONSE') }
    const store = await ready({ ...user, status: 'WITHDRAWN' }); assert.equal(store.getSnapshot().status, 'anonymous'); store.dispose()
  })
  await check('로그인 사용자 공개 목록/상세 Bearer 전달·개인화 응답 보존', async () => {
    const store = await ready(); const post = { ...mockDetail('1', 'owner'), myApplication: { id: 10, postRoleId: 31, status: 'ACCEPTED' } }
    globalThis.fetch = async (url, options) => { assert.ok(options.headers.get('Authorization')); return json(url.includes('?') ? { ...empty, items: [post] } : post) }
    const detail = await store.execute((s, signal) => getPost('1', { token: s.token, signal }))
    assert.equal(detail.isMine, true); assert.equal(detail.myApplication.status, 'ACCEPTED')
    assert.equal((await store.execute((s, signal) => getPosts({}, { token: s.token, signal }))).items.length, 1); store.dispose()
  })
  await check('기존 저장 우선순위/정책 유지·저장 이벤트·계정 변경·로그아웃 동기화', async () => {
    const api = await server.ssrLoadModule('/src/features/auth/api.ts')
    const saved = { session: globalThis.sessionStorage, local: globalThis.localStorage, window: globalThis.window }
    const storage = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key), keys: () => [...map.keys()] } }
    try {
      globalThis.sessionStorage = storage(); globalThis.localStorage = storage(); globalThis.window = new EventTarget()
      const store = createAuthStore(api.readAccessToken, async () => ({ ...user, id: api.readAccessToken() === 'synthetic-a' ? 1 : 2 }))
      window.addEventListener('teamon:auth-changed', () => { void store.sync() })
      api.saveAccessToken('synthetic-a', false); assert.equal(store.getSnapshot().status, 'checking'); await store.refresh()
      assert.equal(store.requireSession().userId, '1'); assert.deepEqual(sessionStorage.keys(), ['teamon.accessToken']); assert.deepEqual(localStorage.keys(), [])
      const epoch = store.getSnapshot().epoch
      api.saveAccessToken('synthetic-b', true); assert.equal(store.getSnapshot().session, undefined); await store.refresh()
      assert.equal(store.requireSession().userId, '2'); assert.ok(store.getSnapshot().epoch > epoch); assert.deepEqual(sessionStorage.keys(), [])
      api.clearAccessToken(); assert.equal(store.getSnapshot().status, 'anonymous'); assert.equal(store.getSnapshot().session, undefined); assert.deepEqual(localStorage.keys(), []); store.dispose()
    } finally { globalThis.sessionStorage = saved.session; globalThis.localStorage = saved.local; globalThis.window = saved.window }
  })
  await check('이전 계정의 늦은 사용자/개인 데이터 응답 폐기·요청 Abort', async () => {
    let token = 'a'; const pending = deferred(), store = createAuthStore(() => token, async value => value === 'a' ? pending.promise : { ...user, id: 2 })
    const loading = store.refresh(); token = 'b'; await store.sync(); pending.resolve(user); await loading; assert.equal(store.requireSession().userId, '2')
    const data = deferred(); let signal
    const request = store.execute(async (_, s) => { signal = s; return data.promise }); token = null; await store.sync()
    assert.equal(signal.aborted, true); data.resolve('old-private-data'); await assert.rejects(request, e => e.name === 'AbortError')
    assert.equal(store.getSnapshot().session, undefined); store.dispose()
  })
  await check('401 세션 제거·403 권한 오류 유지·조회 네트워크 실패 구분·자동 재시도 없음', async () => {
    const store = await ready(); let calls = 0
    await assert.rejects(store.execute(async () => { calls++; throw fault(403, 'FORBIDDEN') })); assert.equal(store.getSnapshot().status, 'authenticated')
    await assert.rejects(store.execute(async () => { calls++; throw fault(401, 'UNAUTHORIZED') })); assert.equal(store.getSnapshot().status, 'anonymous')
    await assert.rejects(store.execute(async () => { calls++ })); assert.equal(calls, 2); store.dispose()
    for (const error of [new TypeError('offline'), fault(403, 'FORBIDDEN')]) {
      let loads = 0; const failed = createAuthStore(() => 'synthetic', async () => { loads++; throw error }); await failed.refresh()
      assert.equal(failed.getSnapshot().status, 'error'); await assert.rejects(failed.execute(async () => {}), e => e.code === 'AUTH_LOOKUP_FAILED'); assert.equal(loads, 1); failed.dispose()
    }
  })
  await check('학교 인증 상태 재조회 반영·토큰이 같아도 사용자 변경 시 화면 세대 변경', async () => {
    let account = { ...user, status: 'UNVERIFIED' }; const store = createAuthStore(() => 'synthetic', async () => account)
    await store.refresh(); assert.throws(() => store.requireSession(true), e => e.code === 'UNVERIFIED')
    account = user; await store.refresh(); assert.equal(store.requireSession(true).account.status, 'VERIFIED')
    const before = store.getSnapshot().epoch; account = { ...user, id: 99 }; await store.refresh(); assert.ok(store.getSnapshot().epoch > before); store.dispose()
  })
  await check('작성 sender 실제 Bearer·미인증/401/403 입력 원본 유지·동일 tick 중복 방지', async () => {
    const store = await ready(), input = draft(), original = structuredClone(input), pending = deferred(); let calls = 0, success = 0
    globalThis.fetch = async (_, options) => { calls++; assert.equal(options.method, 'POST'); assert.ok(options.headers.get('Authorization')); await pending.promise; return json({ id: 30 }) }
    const submitter = createPostSubmitter(), callbacks = { onInvalid: () => assert.fail('invalid'), onStart() {}, onSuccess() { success++ }, onFailure: () => assert.fail('failed'), onSettled() {} }
    const options = { send: (body, opts) => store.execute((s, signal) => createPost(body, { token: s.token, signal: AbortSignal.any([signal, opts.signal]) }), true) }
    const first = submitter.submit(input, callbacks, options); await submitter.submit(input, callbacks, options); assert.equal(calls, 1); pending.resolve(); await first; assert.equal(success, 1); assert.deepEqual(input, original); store.dispose()
    for (const status of [401, 403]) {
      const failed = await ready(); globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: status === 401 ? 'UNAUTHORIZED' : 'FORBIDDEN', message: 'denied' }), { status }); let failure
      await createPostSubmitter().submit(input, { ...callbacks, onFailure: e => { failure = e } }, { send: (body, opts) => failed.execute((s, signal) => createPost(body, { token: s.token, signal: AbortSignal.any([signal, opts.signal]) }), true) })
      assert.ok(failure); assert.deepEqual(input, original); failed.dispose()
    }
  })
  await check('수정 초기 상세/연락처/지원 현황 및 PUT에 동일 사용자 Bearer', async () => {
    const store = await ready(), post = mockDetail('1', 'owner'), paths = []
    globalThis.fetch = async (url, options) => {
      assert.ok(options.headers.get('Authorization')); const path = new URL(url).pathname; paths.push(path)
      return json(path.endsWith('/contact') ? { contactLink: 'https://example.com/contact' } : path.endsWith('/applications') ? post.roles.map(r => ({ postRoleId: r.id, roleName: r.roleName, headcount: r.headcount, filledCount: r.filledCount, applications: [] })) : post)
    }
    const snapshot = await store.execute((s, signal) => edit.loadEditSnapshot('1', { token: s.token, signal }))
    assert.equal(snapshot.post.isMine, true); assert.equal(paths.length, 3)
    const value = edit.editDraft(snapshot), before = structuredClone(value)
    await store.execute((s, signal) => edit.updatePost(1, edit.toEditRequest(value, snapshot), { token: s.token, signal })); assert.deepEqual(value, before); store.dispose()
  })
  await check('지원·마감·연락처·내 모집글/지원·본인 나가기 실제 요청과 인증 전달', async () => {
    const store = await ready(), paths = []
    globalThis.fetch = async (url, options) => {
      assert.ok(options.headers.get('Authorization')); const path = new URL(url).pathname; paths.push(path)
      return json(path.endsWith('/close') ? null : path.endsWith('/contact') ? { contactLink: 'https://example.com/contact' } : path.endsWith('/leave') ? { applicationId: 10, status: 'LEFT', roleReopened: true, postReopened: false } : path === '/api/posts/1/applications' ? { id: 10, status: 'PENDING' } : path === '/api/posts/1' ? { ...mockDetail('1', 'other'), myApplication: { id: 10, postRoleId: 31, status: 'PENDING' } } : empty)
    }
    const run = action => store.execute((s, signal) => action({ token: s.token, signal }))
    await run(o => applyToPost(1, { postRoleId: 31, message: '함께 프로젝트를 만들고 싶습니다. 꾸준히 참여하겠습니다.', availableTime: '주말' }, o))
    const refreshed = await refreshAppliedPost(mockDetail('1', 'other'), () => run(o => getPost('1', o)))
    assert.equal(refreshed.post.myApplication.id, 10); assert.equal(refreshed.refreshFailed, false)
    await run(o => closePost(1, o)); assert.equal(await run(o => getPostContact(1, o)), 'https://example.com/contact')
    await run(o => getMyPosts('OPEN', undefined, o)); await run(o => getMyApplications('', undefined, o)); await run(o => actOnApplication(10, 'leave', o))
    assert.equal(paths.length, 7); store.dispose()
  })
  await check('로그아웃/확인 중 개인 페이지에 이전 목록·연락처 없음·인증 안내 렌더', async () => {
    const { AuthContext } = await server.ssrLoadModule('/src/features/auth/context.ts')
    const { default: MyPostsPage } = await server.ssrLoadModule('/src/pages/MyPostsPage.tsx')
    const { default: MyApplicationsPage } = await server.ssrLoadModule('/src/pages/MyApplicationsPage.tsx')
    const { default: MyScrapsPage } = await server.ssrLoadModule('/src/pages/MyScrapsPage.tsx')
    const { default: PostApplicantsPage } = await server.ssrLoadModule('/src/pages/PostApplicantsPage.tsx')
    let token = 'synthetic', pending = deferred(); const store = createAuthStore(() => token, () => pending.promise)
    const render = Component => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(AuthContext.Provider, { value: store }, createElement(Component))))
    for (const Component of [MyPostsPage, MyApplicationsPage, MyScrapsPage, PostApplicantsPage]) { const html = render(Component); assert.ok(html.includes('확인 중')); assert.ok(!html.includes('https://example.com/contact')) }
    token = null; await store.sync()
    for (const Component of [MyPostsPage, MyApplicationsPage, MyScrapsPage, PostApplicantsPage]) { const html = render(Component); assert.ok(html.includes('로그인')); assert.ok(!html.includes('my-post-card')); assert.ok(!html.includes('my-application-card')) }
    store.dispose()
    const signedIn = await ready()
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/me/posts?status=CLOSED'] }, createElement(AuthContext.Provider, { value: signedIn }, createElement(MyPostsPage))))
    assert.ok(html.includes('aria-pressed="true">마감</button>')); signedIn.dispose()
  })
  await check('StrictMode 정리/재활성·확인 중 늦은 응답 폐기', async () => {
    const pending = deferred(); let loads = 0
    const store = createAuthStore(() => 'synthetic', async () => ++loads === 1 ? pending.promise : user)
    const old = store.refresh(); store.dispose(); store.resume(); await store.refresh(); pending.resolve({ ...user, id: 999 }); await old
    assert.equal(store.requireSession().userId, '7'); store.dispose()
  })
  await check('스크랩·지원자 관리 공통 세션 Bearer 재사용·사용자 중복 조회 없음', async () => {
    const { authenticatedScraps } = await server.ssrLoadModule('/src/features/scraps/source.ts')
    const { authenticatedManagement } = await server.ssrLoadModule('/src/features/applicants/source.ts')
    let lookups = 0
    const store = createAuthStore(() => 'synthetic', async () => { lookups++; return user }); await store.refresh()
    const signal = new AbortController().signal, paths = []
    const post = mockDetail('1', 'owner')
    globalThis.fetch = async (url, options) => {
      assert.ok(options.headers.get('Authorization')); const path = new URL(url).pathname; paths.push(path)
      return json(path.endsWith('/scraps') ? empty : path.endsWith('/scrap') ? { postId: 1, isScrapped: options.method === 'PUT' }
        : path.endsWith('/leave') ? { applicationId: 10, status: 'LEFT', roleReopened: true, postReopened: false }
        : path.endsWith('/applications') ? post.roles.map(r => ({ postRoleId: r.id, roleName: r.roleName, headcount: r.headcount, filledCount: r.filledCount, applications: [] })) : post)
    }
    const scraps = authenticatedScraps(store), management = authenticatedManagement('1', store)
    await scraps.load(undefined, signal); await scraps.change(1, true, signal); await scraps.change(1, false, signal); await scraps.read(1, signal)
    await management.load(signal); await management.decide(10, 'remove', null, signal)
    assert.equal(paths.length, 7); assert.equal(lookups, 1); store.dispose()
  })
  await check('새 보호 기능도 인증 확인 전 차단·로그아웃 이후 늦은 응답 폐기', async () => {
    const { authenticatedScraps } = await server.ssrLoadModule('/src/features/scraps/source.ts')
    const { authenticatedManagement } = await server.ssrLoadModule('/src/features/applicants/source.ts')
    let token = 'synthetic', calls = 0; const pending = deferred()
    const store = createAuthStore(() => token, async () => user), signal = new AbortController().signal
    globalThis.fetch = async () => { calls++; return pending.promise }
    await assert.rejects(authenticatedScraps(store).load(undefined, signal), e => e.code === 'AUTH_CHECKING')
    await assert.rejects(authenticatedManagement('1', store).load(signal), e => e.code === 'AUTH_CHECKING'); assert.equal(calls, 0)
    await store.refresh()
    const request = authenticatedScraps(store).load(undefined, signal)
    const rejected = assert.rejects(request, e => e.name === 'AbortError')
    token = null; await store.sync(); pending.resolve(json(empty)); await rejected
    await assert.rejects(authenticatedManagement('1', store).decide(10, 'remove', null, signal), e => e.code === 'UNAUTHORIZED')
    assert.equal(calls, 1); store.dispose()
  })
  await check('관리자 실제 role 재사용·중복 사용자 조회 없음·일반 사용자 요청 차단', async () => {
    const { authenticatedAdmin } = await server.ssrLoadModule('/src/features/admin/source.ts')
    let calls = 0
    globalThis.fetch = async (_, options) => { calls++; assert.ok(options.headers.get('Authorization')); return json(empty) }
    const signal = new AbortController().signal, store = await ready({ ...user, role: 'ADMIN' })
    const service = authenticatedAdmin(store)
    assert.equal((await service.account(signal)).role, 'ADMIN'); assert.equal(calls, 0)
    await service.list('reports', 'RECEIVED', undefined, signal); assert.equal(calls, 1); store.dispose()
    const ordinary = await ready(), denied = authenticatedAdmin(ordinary)
    assert.equal((await denied.account(signal)).role, 'USER')
    await assert.rejects(denied.list('reports', 'RECEIVED', undefined, signal), e => e.status === 403)
    assert.equal(calls, 1); ordinary.dispose()
  })
  console.log(`${passed} auth session checks passed (intercepted API/state/static render; no real backend/browser).`)
} finally { globalThis.fetch = originalFetch; await server.close() }
