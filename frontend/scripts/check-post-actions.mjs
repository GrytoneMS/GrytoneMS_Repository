// 별도 API 요청 가로채기, 상태 전이·정적 렌더 검증. 실제 서버/DB/브라우저 검증 아님.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'

process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = process.argv.includes('--mock') ? 'true' : 'false'
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let passed = 0
const check = async (name, run) => { await run(); console.log(`PASS ${++passed} ${name}`) }
const envelope = data => new Response(JSON.stringify({ success: true, data }), { status: 200 })
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
try {
  const api = await server.ssrLoadModule('/src/features/posts/post-actions-api.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { mockDetail, mockList } = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const { createPostActions, canClosePost, canReadContact, closedPost } = await server.ssrLoadModule('/src/features/posts/post-actions-controller.ts')
  const { applicationBlock } = await server.ssrLoadModule('/src/features/posts/application-model.ts')
  const { PostActionsView, PostActions } = await server.ssrLoadModule('/src/features/posts/PostActions.tsx')
  const fail = (status, code) => new ApiError(status, { success: false, code, message: '응답에 연락처를 포함해도 UI 오류에 그대로 출력하지 않음' })
  const owner = () => mockDetail('1', 'owner')
  function fixture(post = owner(), overrides = {}) {
    const posts = []
    const actions = createPostActions(post, { close: async () => {}, contact: async () => 'https://example.com/team', load: async () => closedPost(post), onPost: value => posts.push(value), ...overrides })
    return { actions, posts }
  }
  await check('무토큰·공백 토큰: 실제 마감/연락처 API fetch 0회', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; return envelope(null) }
    for (const token of [undefined, '', '   ']) for (const request of [api.closePost, api.getPostContact]) {
      await assert.rejects(request(1, { token }), error => error.code === 'AUTH_PENDING')
    }
    assert.equal(calls, 0)
  })
  await check('정확한 PATCH close·GET contact, body 없음·Bearer·signal 전달', async () => {
    const signal = new AbortController().signal
    const calls = []; globalThis.fetch = async (url, options) => { calls.push({ url, options }); return envelope(url.endsWith('/close') ? null : { contactLink: 'https://example.com/team' }) }
    await api.closePost(123, { token: 'test-token', signal })
    assert.equal(await api.getPostContact(123, { token: 'test-token', signal }), 'https://example.com/team')
    assert.deepEqual(calls.map(call => call.url), ['http://localhost:8080/api/posts/123/close', 'http://localhost:8080/api/posts/123/contact'])
    assert.equal(calls[0].options.method, 'PATCH'); assert.equal(calls[1].options.method, undefined)
    for (const { options } of calls) { assert.equal(options.body, undefined); assert.equal(options.headers.get('Authorization'), 'Bearer test-token'); assert.equal(options.signal, signal) }
  })
  await check('응답 계약·안전 프로토콜·자격증명·제어문자·상대 URL 차단', async () => {
    for (const value of ['', null, 'javascript:alert(1)', 'data:text/html,test', '//example.com', '/contact', 'ftp://example.com', 'https://user:pass@example.com', 'https://example.com/\nteam', 'https://example.com/ team']) {
      assert.equal(api.safeContactLink(value), null)
      globalThis.fetch = async () => envelope({ contactLink: value })
      await assert.rejects(api.getPostContact(1, { token: 'test' }), error => error.code === 'INVALID_RESPONSE')
    }
    assert.equal(api.safeContactLink('http://example.com/team'), 'http://example.com/team')
    for (const value of [{}, undefined, { contactLink: 'https://example.com' }]) {
      globalThis.fetch = async () => envelope(value)
      await assert.rejects(api.closePost(1, { token: 'test' }), error => error.code === 'INVALID_RESPONSE')
    }
  })
  await check('401·403·404·409·500·네트워크 오류를 mock 성공으로 바꾸지 않음', async () => {
    for (const [status, code] of [[401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'POST_ALREADY_CLOSED'], [500, 'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: '오류' }), { status })
      for (const request of [api.closePost, api.getPostContact]) await assert.rejects(request(1, { token: 'test' }), error => error.status === status && error.code === code)
    }
    globalThis.fetch = async () => { throw new TypeError('network') }
    await assert.rejects(api.closePost(1, { token: 'test' }), /network/)
    await assert.rejects(api.getPostContact(1, { token: 'test' }), /network/)
  })
  await check('권한별 표시·마감 글에서도 팀장/수락자 연락처 조회·일반 데이터에 연락처 없음', () => {
    for (const viewer of ['guest', 'other', 'owner', 'applied', 'accepted', 'rejected', 'canceled', 'applicationClosed', 'left']) {
      for (const id of ['1', '12']) {
        const post = mockDetail(id, viewer)
        assert.equal(canReadContact(post), ['owner', 'accepted'].includes(viewer))
        assert.equal(canClosePost(post), viewer === 'owner' && id === '1')
        assert.equal('contactLink' in post, false)
        const { actions } = fixture(post)
        const html = renderToStaticMarkup(createElement(PostActionsView, { state: actions.getSnapshot(), mock: false, authenticated: false }))
        assert.equal(html.includes('연락 수단 확인</button>'), ['owner', 'accepted'].includes(viewer))
        assert.equal(html.includes('모집 마감하기</button>'), viewer === 'owner' && id === '1')
        assert.ok(!html.includes('https://example.com'))
        assert.ok(html.includes('공통 인증 연동 대기 중'))
      }
    }
    for (const item of mockList('LATEST').items) assert.equal('contactLink' in item, false)
  })
  await check('본인 외 마감·미수락 연락처 요청은 controller에서도 차단', async () => {
    let calls = 0
    const { actions } = fixture(mockDetail('1', 'applied'), { close: async () => { calls++ }, contact: async () => { calls++; return 'https://example.com' } })
    await actions.close(); await actions.readContact(); assert.equal(calls, 0)
  })
  await check('같은 틱 연속 마감 1회·성공 즉시 글/역할 갱신·지원 차단·재마감 금지', async () => {
    const pending = deferred(); let calls = 0
    const { actions, posts } = fixture(owner(), { close: () => { calls++; return pending.promise } })
    const first = actions.close(); await actions.close(); assert.equal(calls, 1)
    pending.resolve(); await first
    assert.equal(posts.at(-1).status, 'CLOSED'); assert.ok(posts.at(-1).roles.every(role => role.status === 'CLOSED'))
    assert.ok(applicationBlock({ ...posts.at(-1), isMine: false }))
    await actions.close(); assert.equal(calls, 1)
    const accepted = closedPost(mockDetail('1', 'accepted')); assert.equal(accepted.myApplication.status, 'ACCEPTED')
    assert.equal(closedPost(mockDetail('1', 'applied')).myApplication.status, 'CLOSED')
  })
  await check('마감 성공·후속 조회 실패 분리·재조회만 재시도·늦은 OPEN 응답도 CLOSED 유지', async () => {
    let failLoad = true; let closes = 0
    const { actions } = fixture(owner(), { close: async () => { closes++ }, load: async () => { if (failLoad) throw Error('load'); return owner() } })
    await actions.close(); const state = actions.getSnapshot()
    assert.equal(state.closeConfirmed, true); assert.equal(state.needsRefresh, true); assert.equal(state.post.status, 'CLOSED'); assert.ok(state.closeNotice)
    await actions.close(); assert.equal(closes, 1)
    failLoad = false; await actions.refresh(); assert.equal(actions.getSnapshot().needsRefresh, false); assert.equal(actions.getSnapshot().post.status, 'CLOSED')
  })
  await check('불확실한 마감 실패는 성공 안내 없음·조회 전 재마감 잠금·조회 후 재시도', async () => {
    let ready = false; let calls = 0
    const { actions } = fixture(owner(), { close: async () => { calls++; throw Error('network') }, load: async () => { if (!ready) throw Error('load'); return owner() } })
    await actions.close(); assert.equal(actions.getSnapshot().closeNotice, ''); assert.equal(actions.getSnapshot().closeConfirmed, false)
    assert.equal(actions.getSnapshot().needsRefresh, true); await actions.close(); assert.equal(calls, 1)
    ready = true; await actions.refresh(); await actions.close(); assert.equal(calls, 2)
  })
  await check('이미 마감 오류는 서버 확인 상태 반영·권한/인증 오류 안내·재마감 금지', async () => {
    const { actions } = fixture(owner(), { close: async () => { throw fail(409, 'POST_ALREADY_CLOSED') }, load: async () => { throw Error('load') } })
    await actions.close(); assert.equal(actions.getSnapshot().post.status, 'CLOSED'); assert.equal(actions.getSnapshot().closeNotice, ''); assert.match(actions.getSnapshot().closeError, /이미 마감/)
    for (const [status, code] of [[401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [403, 'SUSPENDED']]) {
      const { actions: other } = fixture(owner(), { close: async () => { throw fail(status, code) }, load: async () => { throw Error('load') } })
      await other.close(); assert.ok(other.getSnapshot().closeError); assert.equal(other.getSnapshot().closeConfirmed, false)
    }
  })
  await check('연락처는 별도 조회 성공 후만 표시·중복 요청 방지·재조회 시 기존 값 제거', async () => {
    let calls = 0; const pending = deferred()
    const { actions } = fixture(mockDetail('1', 'accepted'), { contact: () => { calls++; return pending.promise } })
    assert.equal(actions.getSnapshot().contact, null)
    const first = actions.readContact(); await actions.readContact(); assert.equal(calls, 1); assert.equal(actions.getSnapshot().contact, null)
    pending.resolve('https://example.com/team'); await first; assert.equal(actions.getSnapshot().contact, 'https://example.com/team')
    const next = actions.readContact(); assert.equal(actions.getSnapshot().contact, null); await next
  })
  await check('연락처 조회 실패·권한 변경·복사 실패 후 안내·오류 응답의 민감 문자열 미출력', async () => {
    let denied = false
    const { actions } = fixture(owner(), { contact: async () => { if (denied) throw fail(403, 'FORBIDDEN'); return 'https://example.com/team' } })
    await actions.readContact(); let copied = ''
    await actions.copy(async text => { copied = text }); assert.equal(copied, 'https://example.com/team'); assert.match(actions.getSnapshot().copyNotice, /복사했어요/)
    await actions.copy(async () => { throw Error('clipboard denied') }); assert.match(actions.getSnapshot().copyNotice, /복사하지 못했어요/)
    denied = true; await actions.readContact(); assert.equal(actions.getSnapshot().contact, null); assert.equal(actions.getSnapshot().copyNotice, '')
    assert.ok(actions.getSnapshot().contactError); assert.ok(!actions.getSnapshot().contactError.includes('응답에 연락처'))
    denied = false; await actions.readContact(); assert.ok(actions.getSnapshot().contact)
  })
  await check('숨기기·권한 변경은 즉시 제거하고 늦은 조회/복사 결과 무시', async () => {
    const pending = deferred(); const { actions } = fixture(owner(), { contact: () => pending.promise })
    const first = actions.readContact(); actions.clearContact(); pending.resolve('https://example.com/late'); await first
    assert.equal(actions.getSnapshot().contact, null)
    await actions.readContact(); const copy = deferred(); const copying = actions.copy(() => copy.promise)
    actions.updatePost(mockDetail('1', 'rejected')); assert.equal(actions.getSnapshot().contact, null)
    copy.resolve(); await copying; assert.equal(actions.getSnapshot().copyNotice, '')
  })
  await check('사용자 변경·로그아웃·게시글 이동 cleanup은 abort 및 늦은 응답 폐기', async () => {
    for (const reason of ['user change', 'logout', 'post navigation']) {
      const pending = deferred(); let signal
      const { actions } = fixture(owner(), { contact: next => { signal = next; return pending.promise } })
      const first = actions.readContact(); actions.dispose(); assert.equal(signal.aborted, true, reason)
      pending.resolve('https://example.com/late'); await first; assert.equal(actions.getSnapshot().contact, null, reason)
    }
    const base = { post: owner(), onPost() {}, viewer: 'owner', scenario: 'normal', closing: null, contact: null }
    const a = PostActions({ ...base, session: { userId: 'one', token: 'a' } })
    const b = PostActions({ ...base, session: { userId: 'two', token: 'a' } })
    const c = PostActions(base)
    const d = PostActions({ ...base, post: mockDetail('12', 'owner') })
    const e = PostActions({ ...base, session: { userId: 'one', token: 'b' } })
    assert.notEqual(a.key, b.key); assert.notEqual(a.key, c.key); assert.notEqual(c.key, d.key); assert.notEqual(a.key, e.key)
  })
  await check('폐기 후 마감·재조회 결과가 이전 화면을 갱신하지 않음', async () => {
    const pending = deferred(); const { actions, posts } = fixture(owner(), { close: () => pending.promise })
    const first = actions.close(); actions.dispose(); pending.resolve(); await first; assert.equal(posts.length, 0)
    const load = deferred(); const second = fixture(owner(), { load: () => load.promise })
    const loading = second.actions.refresh(); second.actions.dispose(); load.resolve(owner()); await loading; assert.equal(second.posts.length, 0)
  })
  await check('실제 UI 기본 API도 무토큰이면 요청하지 않고 인증 대기 안내', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; return envelope(null) }
    const { actions } = fixture(owner(), { close: signal => api.closePost(1, { signal }), contact: signal => api.getPostContact(1, { signal }) })
    await actions.close(); await actions.readContact(); assert.equal(calls, 0)
    assert.match(actions.getSnapshot().closeError, /인증 연동/); assert.match(actions.getSnapshot().contactError, /인증 연동/)
  })
  await check('개발 예시는 명시적 모드에서만 실행·실제 fetch 0회·마감/연락/실패/마감 글', async () => {
    const { createMockPostActions } = await server.ssrLoadModule('/src/features/posts/post-actions-mock.ts')
    const { isPostMock } = await server.ssrLoadModule('/src/features/posts/source.ts')
    globalThis.fetch = async () => { throw Error('mock fetch must not run') }
    const signal = new AbortController().signal
    if (!isPostMock) {
      const examples = createMockPostActions(owner(), 'normal', 'owner', null, null)
      await assert.rejects(examples.close(signal), /명시적/); await assert.rejects(examples.contact(signal), /명시적/)
      return
    }
    const examples = createMockPostActions(owner(), 'normal', 'owner', 'success', 'success')
    await examples.close(signal); assert.equal((await examples.load(signal)).status, 'CLOSED')
    assert.equal(await examples.contact(signal), 'https://example.com/team-contact')
    const accepted = createMockPostActions(mockDetail('12', 'accepted'), 'normal', 'accepted', null, null)
    assert.equal(await accepted.contact(signal), 'https://example.com/team-contact')
    for (const viewer of ['guest', 'applied', 'rejected', 'canceled', 'left']) {
      await assert.rejects(createMockPostActions(mockDetail('1', viewer), 'normal', viewer, null, null).contact(signal), error => error.code === 'FORBIDDEN')
    }
    const denied = createMockPostActions(owner(), 'normal', 'owner', 'failure', 'forbidden')
    await assert.rejects(denied.close(signal), error => error.status === 500)
    await assert.rejects(denied.contact(signal), error => error.code === 'FORBIDDEN')
    const refreshFailure = createMockPostActions(owner(), 'normal', 'owner', 'refreshFailure', null)
    await refreshFailure.close(signal); await assert.rejects(refreshFailure.load(signal), /재조회 실패/)
  })
  console.log(`${passed} post action checks passed (intercepted API/state/static render; no real server/DB/browser).`)
} finally { globalThis.fetch = originalFetch; await server.close() }
