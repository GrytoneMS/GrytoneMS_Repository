// 응답 가로채기·상태/정적 렌더 검증입니다. 실제 API·브라우저 검증과 구분합니다.
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
async function check(name, run) { await run(); passed++; console.log(`PASS ${name}`) }
const success = data => new Response(JSON.stringify({ success: true, data }), { status: 200 })
const page = (ids, nextCursor = null) => ({ items: ids.map(id => ({ id })), nextCursor, hasNext: nextCursor !== null })
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
try {
  const q = await server.ssrLoadModule('/src/features/posts/list-query.ts')
  const api = await server.ssrLoadModule('/src/features/posts/api.ts')
  const source = await server.ssrLoadModule('/src/features/posts/source.ts')
  const { createListLoader } = await server.ssrLoadModule('/src/features/posts/list-loader.ts')
  const conditions = q.readListConditions(new URLSearchParams('sort=DEADLINE&category=PROJECT&category=STUDY&roleCode=DEV&roleCode=DESIGN&schoolId=3&schoolId=7&locationCode=CAMPUS&locationCode=CUSTOM&meetingType=ONLINE&meetingType=HYBRID&period=MONTH&openOnly=false&keyword=%20해커톤%20&cursor=old'))
  await check('URL 기본값·다중 선택 복원·잘못된 코드 제외·Long ID 정밀도', () => {
    assert.deepEqual(q.readListConditions(new URLSearchParams()), q.defaultConditions())
    assert.equal(conditions.keyword, '해커톤'); assert.equal(conditions.openOnly, false)
    const parsed = q.readListConditions(new URLSearchParams('category=STUDY,PROJECT&category=STUDY&category=BAD&roleCode=DEV&roleCode=BAD&period=BAD&schoolId=-1&schoolId=9223372036854775807&schoolId=9223372036854775808'))
    assert.deepEqual(parsed.category, ['PROJECT', 'STUDY']); assert.deepEqual(parsed.roleCode, ['DEV'])
    assert.deepEqual(parsed.schoolId, ['9223372036854775807']); assert.equal(parsed.period, undefined)
  })
  await check('필터·전체 조건 초기화 및 URL 왕복, cursor/size 제거', () => {
    const params = q.listSearchParams(conditions, new URLSearchParams('cursor=old&size=1&scenario=normal'))
    assert.equal(params.has('cursor'), false); assert.equal(params.has('size'), false)
    assert.equal(params.get('scenario'), 'normal'); assert.deepEqual(q.readListConditions(params), conditions)
    const reset = q.resetFilters(conditions)
    assert.equal(reset.keyword, '해커톤'); assert.equal(reset.sort, 'DEADLINE'); assert.equal(reset.openOnly, true)
    for (const key of q.multiFilterKeys) assert.deepEqual(reset[key], [])
    assert.deepEqual(q.clearAllConditions(conditions), { ...q.defaultConditions(), sort: 'DEADLINE' })
    // 뒤로가기/새로고침은 URL만으로 같은 조건을 복구합니다.
    assert.deepEqual(q.readListConditions(new URLSearchParams(params.toString())), conditions)
  })
  await check('실제 파라미터 이름 반복·조건 유지·opaque cursor·signal 전달', async () => {
    const calls = []
    globalThis.fetch = async (url, options) => { calls.push({ url, options }); return success(calls.length === 1 ? page([2], 'opaque+/=?한글') : page([1])) }
    const controller = new AbortController()
    const first = await source.loadPosts(conditions.sort, undefined, 'normal', controller.signal, {}, conditions)
    await source.loadPosts(conditions.sort, first.nextCursor, 'normal', controller.signal, {}, conditions)
    const firstQuery = new URL(calls[0].url).searchParams
    const secondQuery = new URL(calls[1].url).searchParams
    for (const key of q.multiFilterKeys) assert.deepEqual(firstQuery.getAll(key), conditions[key])
    assert.equal(firstQuery.has('categories'), false); assert.equal(firstQuery.has('cursor'), false)
    assert.equal(firstQuery.get('size'), '20'); assert.equal(firstQuery.get('period'), 'MONTH')
    assert.equal(firstQuery.get('openOnly'), 'false'); assert.equal(firstQuery.get('keyword'), '해커톤')
    assert.equal(secondQuery.get('cursor'), 'opaque+/=?한글'); secondQuery.delete('cursor')
    assert.equal(firstQuery.toString(), secondQuery.toString()); assert.equal(calls[1].options.signal, controller.signal)
  })
  await check('검색어 trim·1자 차단·빈 검색 해제, 서버로 보내기 전 검증', async () => {
    let calls = 0
    globalThis.fetch = async () => { calls++; return success(page([])) }
    await assert.rejects(api.getPosts({ keyword: ' 가 ' }), error => error.status === 400 && /2자/.test(error.message))
    assert.equal(calls, 0)
    await api.getPosts({ keyword: '  ' }); assert.equal(calls, 1)
    assert.equal(q.keywordError(' 가나 '), ''); assert.equal(q.keywordError(''), '')
  })
  await check('이전 응답이 abort를 무시해도 최신 첫 페이지를 덮어쓰지 않음', async () => {
    const requests = []
    const loader = createListLoader((cursor, signal) => { const result = deferred(); requests.push({ ...result, cursor, signal }); return result.promise })
    const old = loader.first(); const latest = loader.first()
    assert.equal(requests[0].signal.aborted, true)
    requests[1].resolve(page([20])); await latest
    requests[0].resolve(page([10], 'old')); await old
    assert.deepEqual(loader.getSnapshot().items.map(item => item.id), [20])
    assert.equal(loader.getSnapshot().nextCursor, null)
  })
  await check('조건 교체·언마운트 뒤 늦은 오류 무시, 첫 페이지 커서 초기화', async () => {
    const oldResult = deferred()
    const oldLoader = createListLoader(() => oldResult.promise)
    const pending = oldLoader.first(); oldLoader.cancel()
    const nextLoader = createListLoader(async cursor => { assert.equal(cursor, undefined); return page([30]) })
    await nextLoader.first(); oldResult.reject(new Error('늦은 오류')); await pending
    assert.equal(oldLoader.getSnapshot().error, '')
    assert.deepEqual(nextLoader.getSnapshot().items.map(item => item.id), [30])
  })
  await check('더 보기 중복 방지·중복 ID 제거·종료 및 추가 오류 재시도', async () => {
    const more = deferred(); let calls = 0
    const cursors = []
    const loader = createListLoader((cursor) => {
      calls++; cursors.push(cursor)
      if (calls === 1) return Promise.resolve(page([3, 2], 'next'))
      if (calls === 2) return more.promise
      return Promise.resolve(page([2, 1]))
    })
    await loader.first(); const pending = loader.more(); await loader.more(); assert.equal(calls, 2)
    more.reject(new Error('추가 실패')); await pending
    assert.equal(loader.getSnapshot().moreError, '추가 실패'); assert.equal(loader.getSnapshot().nextCursor, 'next')
    assert.deepEqual(loader.getSnapshot().items.map(item => item.id), [3, 2])
    await loader.more(); assert.deepEqual(cursors, [undefined, 'next', 'next'])
    assert.deepEqual(loader.getSnapshot().items.map(item => item.id), [3, 2, 1])
    await loader.more(); assert.equal(calls, 3); assert.equal(loader.getSnapshot().hasNext, false)
  })
  await check('첫 요청 오류·재시도와 로딩·빈 성공 구분', async () => {
    let calls = 0
    const loader = createListLoader(async () => { if (++calls === 1) throw new Error('첫 요청 실패'); return page([]) })
    await loader.first(); assert.equal(loader.getSnapshot().error, '첫 요청 실패'); assert.equal(loader.getSnapshot().loading, false)
    await loader.first(); assert.equal(loader.getSnapshot().error, ''); assert.equal(loader.getSnapshot().loading, false)
    assert.deepEqual(loader.getSnapshot().items, [])
  })
  await check('더 보기 진행 중 첫 페이지 재조회가 늦은 추가 응답을 폐기', async () => {
    const more = deferred(); let calls = 0
    const loader = createListLoader(async () => {
      if (++calls === 1) return page([10], 'next')
      if (calls === 2) return more.promise
      return page([30])
    })
    await loader.first(); const pending = loader.more()
    await loader.first(); more.resolve(page([9], 'stale')); await pending
    assert.deepEqual(loader.getSnapshot().items.map(item => item.id), [30])
    assert.equal(loader.getSnapshot().nextCursor, null)
  })
  await check('학교 API 빈 응답 보존·실패 전파, 가짜 학교 미삽입', async () => {
    globalThis.fetch = async url => { assert.equal(url, 'http://localhost:8080/api/schools'); return success([]) }
    assert.deepEqual(await api.getSchools(), [])
    globalThis.fetch = async () => success([{ id: 3, name: '테스트 학교', campus: null }])
    assert.equal((await api.getSchools())[0].id, 3)
    globalThis.fetch = async () => success([{ id: 'bad', name: '잘못된 응답' }])
    await assert.rejects(api.getSchools(), error => error.code === 'INVALID_RESPONSE')
  })
  await check('API 실패에서 mock 전환 없음', async () => {
    assert.equal(source.isPostMock, false)
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'INVALID_INPUT', message: '서버 검증 실패' }), { status: 400 })
    await assert.rejects(source.loadPosts('LATEST', undefined, 'normal', new AbortController().signal, {}, conditions), error => error.status === 400)
    globalThis.fetch = async () => { throw new TypeError('network failed') }
    await assert.rejects(source.loadPosts('LATEST', undefined, 'normal', new AbortController().signal, {}, conditions), /network failed/)
  })
  await check('최근 검색어 10개·중복 제거·삭제 후 복구·손상된 저장소', () => {
    let recent = []
    for (let i = 0; i < 12; i++) recent = q.rememberSearch(recent, `검색${i}`)
    assert.equal(recent.length, 10)
    recent = q.rememberSearch(recent, ' 검색5 ')
    assert.equal(recent[0], '검색5'); assert.equal(recent.filter(value => value === '검색5').length, 1)
    assert.deepEqual(q.parseRecentSearches(JSON.stringify(recent.filter(value => value !== '검색5'))), recent.slice(1))
    assert.deepEqual(q.parseRecentSearches('{bad'), []); assert.deepEqual(q.parseRecentSearches('{}'), [])
  })
  await check('명시적 mock 모드도 필터 후 페이지 생성, 학교 ID 부재는 명확히 안내', async () => {
    const mock = await server.ssrLoadModule('/src/features/posts/mock.ts')
    const filters = { category: ['CONTEST', 'PROJECT'], openOnly: true }
    const first = mock.mockList('LATEST', undefined, 2, filters)
    const next = mock.mockList('LATEST', first.nextCursor, 20, filters)
    assert.equal(first.hasNext, true)
    assert.ok([...first.items, ...next.items].every(item => filters.category.includes(item.category) && item.status === 'OPEN'))
    assert.equal(new Set([...first.items, ...next.items].map(item => item.id)).size, first.items.length + next.items.length)
    assert.equal(mock.mockList('LATEST', undefined, 20, { keyword: '없는 검색어' }).items.length, 0)
    assert.throws(() => mock.mockList('LATEST', undefined, 20, { schoolId: ['3'] }), /학교 ID가 없습니다/)
  })
  await check('URL 선택값 정적 렌더·검색 폼·접근성 필터·1자 URL 오류', async () => {
    const { default: PostListPage } = await server.ssrLoadModule('/src/pages/PostListPage.tsx')
    const render = path => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(PostListPage)))
    const html = render(`/posts?${q.listSearchParams(conditions)}`)
    assert.ok(html.includes('role="search"')); assert.ok(html.includes('value="해커톤"'))
    assert.ok(html.includes('type="checkbox" checked=""')); assert.ok(html.includes('type="radio" name="post-period" checked=""'))
    assert.ok(html.includes('모집중만 보기 OFF')); assert.ok(html.includes('aria-pressed="false"')); assert.ok(html.includes('학교 ID 3'))
    const openOnly = render('/posts?openOnly=true')
    assert.ok(openOnly.includes('모집중만 보기 ON')); assert.ok(openOnly.includes('aria-pressed="true"'))
    assert.ok(html.includes('posts/search.svg')); assert.ok(!html.includes('준비 중인 모집글 필터'))
    const invalid = render('/posts?keyword=가')
    assert.ok(invalid.includes('검색어는 2자 이상')); assert.ok(invalid.includes('role="alert"'))
  })
  console.log(`${passed} checks passed (자동 검증, 실제 API/브라우저 검증 아님).`)
} finally { globalThis.fetch = originalFetch; await server.close() }
