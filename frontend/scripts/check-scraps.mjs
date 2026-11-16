// fetch 가로채기·제어 상태·정적 렌더 검증. 실제 JWT/DB/브라우저 검증은 아닙니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let mockServer, passed = 0
const check = async (name, fn) => { await fn(); console.log(`PASS ${++passed} ${name}`) }
const deferred = () => { let resolve, reject; const promise = new Promise((r, j) => { resolve = r; reject = j }); return { promise, resolve, reject } }
const success = data => new Response(JSON.stringify({ success: true, data }))
try {
  const api = await server.ssrLoadModule('/src/features/scraps/api.ts')
  const { createScrapController } = await server.ssrLoadModule('/src/features/scraps/controller.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { mockList } = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const post = { ...mockList('LATEST').items[0], isScrapped: true }
  const row = (scrapId = 10, postId = 1) => ({ scrapId, scrappedAt: '2026-11-16T12:00:00', post: { ...post, id: postId } })
  const page = (items = [row()], nextCursor = null) => ({ items, nextCursor, hasNext: nextCursor !== null })
  const base = () => ({ load: async () => page(), change: async (postId, saved) => ({ postId, isScrapped: saved }), read: async postId => ({ postId, isScrapped: true }) })
  const token = { token: 'test-token' }
  await check('무토큰·빈값·공백: 목록/등록/해제/상태 조회 fetch 0회', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('must not fetch') }
    for (const value of [undefined, '', '  ']) {
      const options = { token: value }
      await assert.rejects(api.getMyScraps(undefined, options), e => e.code === 'AUTH_PENDING')
      for (const saved of [true, false]) await assert.rejects(api.changeScrap(1, saved, options), e => e.code === 'AUTH_PENDING')
      await assert.rejects(api.realScraps(options).read(1, new AbortController().signal), e => e.code === 'AUTH_PENDING')
    }
    assert.equal(calls, 0)
  })
  await check('PUT/DELETE 경로·본문 없음·응답 isScrapped·Bearer/signal', async () => {
    const signal = new AbortController().signal
    for (const saved of [true, false]) {
      globalThis.fetch = async (url, options) => {
        assert.equal(url, 'http://localhost:8080/api/posts/1/scrap'); assert.equal(options.method, saved ? 'PUT' : 'DELETE')
        assert.equal(options.body, undefined); assert.equal(options.signal, signal); assert.equal(options.headers.get('Authorization'), 'Bearer test-token')
        return success({ postId: 1, isScrapped: saved })
      }
      assert.deepEqual(await api.changeScrap(1, saved, { ...token, signal }), { postId: 1, isScrapped: saved })
    }
  })
  await check('최신 스크랩 ID 커서·size20·Long 원문·최초 cursor 생략', async () => {
    for (const cursor of [undefined, '9223372036854775807']) {
      globalThis.fetch = async url => {
        const query = new URL(url).searchParams
        assert.equal(new URL(url).pathname, '/api/users/me/scraps'); assert.equal(query.get('size'), '20'); assert.equal(query.get('cursor'), cursor ?? null)
        assert.equal(query.has('sort'), false); return success(page())
      }
      await api.getMyScraps(cursor, token)
    }
  })
  await check('잘못된 상태·ID·중복·순서·순환 커서 응답 거부', async () => {
    for (const value of [page([row(10), row(11, 2)]), page([row(10), row(9)]), page([row(10), row(10, 2)]), page([{ ...row(), post: { ...post, isScrapped: false } }]), page([{ ...row(), post: { ...post, status: 'DELETED' } }]), page([], '10'), page([row()], '9')]) {
      assert.throws(() => api.validateScraps(value), e => e.code === 'INVALID_RESPONSE')
    }
    assert.throws(() => api.validateScraps(page([row()], '10'), '10'), e => e.code === 'INVALID_RESPONSE')
    for (const result of [{ postId: 2, isScrapped: true }, { postId: 1, isScrapped: false }, null]) {
      globalThis.fetch = async () => success(result)
      await assert.rejects(api.changeScrap(1, true, token), e => e.code === 'INVALID_RESPONSE')
    }
  })
  await check('400 본인 글·403 미인증·401/404/500·네트워크 오류 전파', async () => {
    for (const [status, code] of [[400, 'CANNOT_SCRAP_OWN_POST'], [403, 'UNVERIFIED'], [401, 'UNAUTHORIZED'], [404, 'NOT_FOUND'], [500, 'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: code }), { status })
      await assert.rejects(api.changeScrap(1, true, token), e => e.status === status && e.code === code)
    }
    globalThis.fetch = async () => { throw new TypeError('network') }
    await assert.rejects(api.getMyScraps(undefined, token), /network/)
  })
  await check('서버 초기값·본인 글 등록 차단·해제는 허용·마감 글 상태 유지', async () => {
    let calls = 0
    const c = createScrapController({ ...base(), change: async (id, saved) => { calls++; return { postId: id, isScrapped: saved } } })
    await c.change(1, false, true); assert.equal(calls, 0); assert.match(c.getSnapshot().errors[1], /내가 올린/)
    await c.change(1, true, true); assert.equal(calls, 1); assert.equal(c.getSnapshot().values[1], false)
    c.dispose()
  })
  await check('같은 tick 중복 등록/해제 1회·성공 후 공유 상태 갱신', async () => {
    let calls = 0; const pending = deferred()
    const c = createScrapController({ ...base(), change: async () => { calls++; return pending.promise } })
    const first = c.change(1, false); await c.change(1, false)
    assert.equal(calls, 1); assert.equal(c.getSnapshot().values[1], undefined); assert.equal(c.getSnapshot().busy[1], true)
    pending.resolve({ postId: 1, isScrapped: true }); await first
    assert.equal(c.getSnapshot().values[1], true); assert.equal(c.getSnapshot().busy[1], false)
    c.dispose()
  })
  await check('등록·해제 실패는 기존 상태/목록 유지와 오류 표시', async () => {
    const c = createScrapController({ ...base(), change: async () => { throw new ApiError(500, { success: false, code: 'INTERNAL_ERROR', message: 'fail' }) } })
    await c.first(); await c.change(1, true)
    assert.equal(c.getSnapshot().items.length, 1); assert.equal(c.getSnapshot().values[1], true); assert.ok(c.getSnapshot().errors[1])
    await c.change(2, false); assert.equal(c.getSnapshot().values[2], undefined)
    c.dispose()
  })
  await check('빈 목록·첫 조회 실패/재시도·더 보기 커서/중복 방지/실패 시 항목 유지', async () => {
    let fail = true, calls = 0
    const pending = deferred()
    const c = createScrapController({ ...base(), load: async cursor => {
      calls++; if (fail) throw Error('fail')
      return cursor ? pending.promise : page([row()], '10')
    } })
    await c.first(); assert.ok(c.getSnapshot().error); fail = false; await c.first(); assert.equal(c.getSnapshot().items.length, 1)
    const first = c.more(); await c.more(); assert.equal(calls, 3)
    pending.reject(Error('more failed')); await first
    assert.equal(c.getSnapshot().items.length, 1); assert.equal(c.getSnapshot().cursor, '10'); assert.ok(c.getSnapshot().moreError)
    const empty = createScrapController({ ...base(), load: async () => page([]) }); await empty.first(); assert.equal(empty.getSnapshot().loaded, true); assert.equal(empty.getSnapshot().items.length, 0)
    c.dispose(); empty.dispose()
  })
  await check('더 보기 중복 항목 제거·스크랩 ID가 아닌 모집글 ID로 상세/해제', async () => {
    const c = createScrapController({ ...base(), load: async cursor => cursor ? page([row(9), row(8, 2)]) : page([row()], '10') })
    await c.first(); await c.more(); assert.deepEqual(c.getSnapshot().items.map(r => r.post.id), [1, 2]); c.dispose()
  })
  await check('마지막 항목 해제 후 빈 목록·남은 페이지 자동 재조회', async () => {
    let changed = false
    for (const remaining of [[], [row(9, 2)]]) {
      changed = false
      const c = createScrapController({ ...base(), load: async () => changed ? page(remaining) : page([row()], '10'), change: async (postId, saved) => { changed = true; return { postId, isScrapped: saved } } })
      await c.first(); await c.change(1, true)
      assert.deepEqual(c.getSnapshot().items.map(r => r.post.id), remaining.map(r => r.post.id)); assert.equal(c.getSnapshot().hasNext, false); c.dispose()
    }
  })
  await check('해제 성공 후 조회 실패는 성공 유지·항목 재등장/재처리 유도 없음', async () => {
    let changed = false
    const c = createScrapController({ ...base(), load: async () => { if (changed) throw Error('refresh'); return page() }, change: async (postId, saved) => { changed = true; return { postId, isScrapped: saved } } })
    await c.first(); await c.change(1, true)
    assert.equal(c.getSnapshot().values[1], false); assert.equal(c.getSnapshot().items.length, 0); assert.match(c.getSnapshot().error, /변경은 완료/); c.dispose()
  })
  await check('늦은 목록 응답이 해제된 항목을 되살리지 않음', async () => {
    const pending = deferred(); let calls = 0
    const c = createScrapController({ ...base(), load: async () => ++calls === 1 ? page() : calls === 2 ? pending.promise : page([]) })
    await c.first(); const old = c.refresh(); await c.change(1, true)
    pending.resolve(page()); await old
    assert.equal(c.getSnapshot().items.length, 0); assert.equal(c.getSnapshot().values[1], false); c.dispose()
  })
  await check('첫 목록 조회 중 처리 성공도 목록 재조회·단순 조회 실패를 변경 성공으로 표시하지 않음', async () => {
    let calls = 0; const pending = deferred()
    const c = createScrapController({ ...base(), load: async () => ++calls === 1 ? pending.promise : page([]) })
    const old = c.first(); await c.change(1, true); pending.resolve(page()); await old
    assert.equal(calls, 2); assert.equal(c.getSnapshot().loaded, true); assert.equal(c.getSnapshot().items.length, 0); c.dispose()
    const d = createScrapController({ ...base(), load: async () => { throw Error('failed') } })
    await d.refresh(); assert.ok(!d.getSnapshot().error.includes('변경은 완료')); d.dispose()
  })
  await check('응답 유실은 기존 표시·재처리 잠금·상태만 재조회 후 복구', async () => {
    let calls = 0
    const c = createScrapController({ ...base(), change: async () => { calls++; throw new TypeError('response lost') } })
    await c.change(1, false); await c.change(1, false)
    assert.equal(calls, 1); assert.equal(c.getSnapshot().values[1], undefined); assert.equal(c.getSnapshot().needsCheck[1], true)
    await c.recheck(1); assert.equal(c.getSnapshot().values[1], true); assert.equal(c.getSnapshot().needsCheck[1], false); c.dispose()
  })
  await check('사용자 변경/폐기 뒤 늦은 목록·처리 무시·Abort 전달', async () => {
    const pending = deferred(); let signal
    const c = createScrapController({ ...base(), load: async (_, s) => { signal = s; return pending.promise } })
    const loading = c.first(); c.dispose(); assert.equal(signal.aborted, true); pending.resolve(page()); await loading; assert.equal(c.getSnapshot().items.length, 0)
    const mutation = deferred(), d = createScrapController({ ...base(), change: async () => mutation.promise })
    const changing = d.change(1, false); d.dispose(); mutation.resolve({ postId: 1, isScrapped: true }); await changing; assert.equal(d.getSnapshot().values[1], undefined)
  })
  await check('인증 만료 시 이전 목록·공유 상태 제거', async () => {
    const c = createScrapController({ ...base(), change: async () => { throw new ApiError(401, { success: false, code: 'UNAUTHORIZED', message: 'expired' }) } }, { 1: true })
    await c.first(); await c.change(1, true); assert.equal(c.getSnapshot().items.length, 0); assert.deepEqual(c.getSnapshot().values, {}); c.dispose()
  })
  await check('오래된 목록/상세 isScrapped 무시·새 서버 스냅샷은 반영', async () => {
    const c = createScrapController(base()), oldRevision = c.getRevision()
    await c.change(1, false)
    c.observe([{ id: 1, isScrapped: false }], oldRevision); assert.equal(c.getSnapshot().values[1], true)
    c.observe([{ id: 1, isScrapped: false }], c.getRevision()); assert.equal(c.getSnapshot().values[1], false)
    c.dispose(); c.resume(); await c.first(); assert.equal(c.getSnapshot().loaded, true); c.dispose()
  })
  await check('이후 서버에서 다시 등록된 스크랩도 최신 목록 상태로 복원', async () => {
    let rows = []
    const c = createScrapController({ ...base(), load: async () => page(rows) }, { 1: false })
    await c.first(); rows = [row()]; await c.first()
    assert.equal(c.getSnapshot().items.length, 1); assert.equal(c.getSnapshot().values[1], true); c.dispose()
  })
  await check('동시 다른 글 처리에서 마지막 요청 실패해도 성공한 해제 목록 재조회', async () => {
    let changed = false, loads = 0; const pending = deferred()
    const c = createScrapController({ ...base(), load: async () => { loads++; return page(changed ? [] : [row()]) }, change: async (postId, saved) => {
      if (postId === 2) return pending.promise
      changed = true; return { postId, isScrapped: saved }
    } })
    await c.first(); const failing = c.change(2, false); await c.change(1, true)
    pending.reject(new ApiError(500, { success: false, code: 'INTERNAL_ERROR', message: 'failed' })); await failing
    assert.equal(loads, 2); assert.equal(c.getSnapshot().items.length, 0); c.dispose()
  })
  await check('북마크는 카드 링크 바깥 독립 버튼·서버 초기값·상태/상세 경로 유지', async () => {
    const { PostCard } = await server.ssrLoadModule('/src/features/posts/components.tsx')
    const { ScrapContext } = await server.ssrLoadModule('/src/features/scraps/context.ts')
    const controller = createScrapController(base())
    const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(ScrapContext.Provider, { value: { controller, mock: false, search: '' } }, createElement(PostCard, { post: { ...post, status: 'CLOSED' }, index: 0, search: '', scrapList: true }))))
    assert.ok(html.includes('href="/posts/1"')); assert.ok(html.includes('aria-pressed="true"')); assert.ok(html.includes('마감'))
    assert.ok(!/<a\b[^>]*>[\s\S]*?<button\b[\s\S]*?<\/a>/.test(html)); assert.ok(html.indexOf('</a>') < html.indexOf('스크랩 해제'))
    controller.dispose()
  })
  await check('내 스크랩 경로와 기존 경로·무관한 하위 경로 분리', async () => {
    const savedStorage = { session: globalThis.sessionStorage, local: globalThis.localStorage }
    globalThis.sessionStorage = { getItem: () => null }
    globalThis.localStorage = { getItem: () => null }
    try {
    const { default: App } = await server.ssrLoadModule('/src/App.tsx')
    const render = path => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(App)))
    for (const path of ['/posts', '/posts/1', '/posts/new', '/posts/1/edit', '/posts/1/applicants', '/me/posts', '/me/applications', '/me/scraps']) assert.ok(render(path).includes('class="posts-layout"'), path)
    for (const path of ['/me/scraps/other', '/me/scraps-extra', '/me/profile']) assert.ok(render(path).includes('class="app-layout"'), path)
    } finally {
      globalThis.sessionStorage = savedStorage.session
      globalThis.localStorage = savedStorage.local
    }
  })
  process.env.VITE_POSTS_MOCK = 'true'
  mockServer = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
  const { mockScraps } = await mockServer.ssrLoadModule('/src/features/scraps/mock.ts')
  await check('예시는 명시 쿼리 필요·최신순/마감/더보기·삭제/숨김 제외·사용자 격리', async () => {
    assert.throws(() => mockScraps(new URLSearchParams()))
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('must not fetch') }
    const params = new URLSearchParams('scrapExample=true&scrapReset=test')
    const alice = mockScraps(params), signal = new AbortController().signal
    const first = await alice.service.load(undefined, signal), second = await alice.service.load(first.nextCursor, signal)
    assert.equal(first.items.length, 3); assert.equal(second.items.length, 3); assert.equal(second.hasNext, false)
    assert.equal(first.items[0].post.status, 'CLOSED'); assert.ok(first.items[0].scrapId > first.items[1].scrapId)
    const bob = mockScraps(new URLSearchParams('scrapExample=true&scrapUser=bob&scrapReset=test'))
    assert.deepEqual((await bob.service.load(undefined, signal)).items.map(r => r.post.id), [3])
    const restricted = mockScraps(new URLSearchParams('scrapExample=true&scrapCase=restricted&scrapReset=test'))
    assert.ok((await restricted.service.load(undefined, signal)).items.every(r => ![10, 8].includes(r.post.id)))
    assert.equal(calls, 0)
  })
  await check('예시 등록/해제 후 목록·상세 상태 공유·마지막 항목·미인증 해제 허용', async () => {
    const signal = new AbortController().signal, params = new URLSearchParams('scrapExample=true&scrapCase=last&scrapReset=actions')
    const example = mockScraps(params)
    await example.service.change(12, false, signal); assert.equal((await example.service.load(undefined, signal)).items.length, 0)
    await example.service.change(1, true, signal); assert.equal((await example.service.read(1, signal)).isScrapped, true)
    assert.equal(mockScraps(params).values[1], true)
    const unverified = mockScraps(new URLSearchParams('scrapExample=true&scrapResult=unverified&scrapReset=unverified'))
    await assert.rejects(unverified.service.change(1, true, signal), e => e.code === 'UNVERIFIED'); await unverified.service.change(12, false, signal)
  })
  console.log(`${passed} scrap checks passed (no real API/DB/browser).`)
} finally { globalThis.fetch = originalFetch; await mockServer?.close(); await server.close() }
