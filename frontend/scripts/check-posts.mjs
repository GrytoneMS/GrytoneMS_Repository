// 요청 계약·데이터 상태 검증입니다. 브라우저나 실제 백엔드 통신 검증을 대신하지 않습니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'

const originalFetch = globalThis.fetch
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
let passed = 0
async function check(name, run) {
  await run()
  passed++
  console.log(`PASS ${name}`)
}
function success(data) { return new Response(JSON.stringify({ success: true, data }), { status: 200 }) }
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let mockServer
try {
  const api = await server.ssrLoadModule('/src/features/posts/api.ts')
  const model = await server.ssrLoadModule('/src/features/posts/model.ts')
  const mock = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const source = await server.ssrLoadModule('/src/features/posts/source.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { postErrorMessage } = await server.ssrLoadModule('/src/features/posts/errors.ts')
  const calls = []
  globalThis.fetch = async (url, options) => { calls.push({ url, options }); return success(mock.mockList('LATEST')) }
  await check('기본 목록: LATEST, size=20, 최초 cursor 및 인증 헤더 생략', async () => {
    const page = await api.getPosts()
    const call = calls.at(-1)
    assert.equal(call.url, 'http://localhost:8080/api/posts?sort=LATEST&size=20')
    assert.equal(call.options.headers.get('Authorization'), null)
    assert.equal(page.hasNext, false)
  })
  await check('DEADLINE, opaque cursor, 선택 토큰 및 AbortSignal 전달', async () => {
    const cursor = 'opaque+/=?한글'
    const controller = new AbortController()
    await api.getPosts({ sort: 'DEADLINE', cursor }, { token: 'test-token', signal: controller.signal })
    const call = calls.at(-1)
    const query = new URL(call.url).searchParams
    assert.equal(query.get('sort'), 'DEADLINE'); assert.equal(query.get('cursor'), cursor)
    assert.equal(call.options.headers.get('Authorization'), 'Bearer test-token')
    assert.equal(call.options.signal, controller.signal)
  })
  await check('상세 경로 및 응답의 isMine/myApplication 보존', async () => {
    const detail = mock.mockDetail('1', 'applied')
    globalThis.fetch = async (url, options) => { calls.push({ url, options }); return success(detail) }
    const result = await api.getPost('1')
    assert.equal(calls.at(-1).url, 'http://localhost:8080/api/posts/1')
    assert.equal(result.isMine, false); assert.equal(result.myApplication.status, 'PENDING')
  })
  await check('잘못된 hasNext/커서 응답을 성공으로 처리하지 않음', async () => {
    for (const page of [
      { items: [], hasNext: true, nextCursor: null },
      { items: [], hasNext: true, nextCursor: 'same' },
      { items: [], nextCursor: null },
    ]) {
      globalThis.fetch = async () => success(page)
      await assert.rejects(api.getPosts({ cursor: 'same' }), error => error.code === 'INVALID_RESPONSE')
    }
    // 최종 여부는 커서의 존재가 아니라 hasNext가 결정합니다.
    globalThis.fetch = async () => success({ items: [], hasNext: false, nextCursor: 'unused' })
    assert.equal((await api.getPosts()).hasNext, false)
  })
  await check('API 모드: HTTP 오류·네트워크 오류에서 mock으로 전환하지 않음', async () => {
    assert.equal(source.isPostMock, false)
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'NOT_FOUND', message: '없음' }), { status: 404 })
    await assert.rejects(source.loadPost('1', 'normal', new AbortController().signal), error => error.status === 404 && error.code === 'NOT_FOUND')
    globalThis.fetch = async () => { throw new TypeError('network failed') }
    await assert.rejects(source.loadPosts('LATEST', undefined, 'normal', new AbortController().signal), /network failed/)
    assert.match(postErrorMessage(new ApiError(404, { success: false, code: 'NOT_FOUND', message: '없음' })), /찾을 수 없어요/)
  })
  await check('명세의 fieldErrors 배열 및 기존 Record 오류 형식 보존', async () => {
    for (const fieldErrors of [[{ field: 'cursor', message: '잘못된 커서' }], { cursor: '잘못된 커서' }]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'INVALID_INPUT', message: '입력 확인', fieldErrors }), { status: 400 })
      await assert.rejects(api.getPosts({ cursor: 'bad' }), error => error.code === 'INVALID_INPUT' && JSON.stringify(error.fieldErrors) === JSON.stringify(fieldErrors))
    }
  })
  await check('mock 목록: 실제 DTO 필드만 반환, 정렬 및 중복 없는 커서 페이지', () => {
    const first = mock.mockList('LATEST', undefined, 8)
    const second = mock.mockList('LATEST', first.nextCursor, 8)
    assert.equal(first.hasNext, true); assert.equal(second.hasNext, false)
    assert.equal(new Set([...first.items, ...second.items].map(item => item.id)).size, 12)
    assert.ok(first.items[0].createdAt > first.items[1].createdAt)
    const sorted = mock.mockList('DEADLINE').items.map(item => item.deadline)
    assert.deepEqual(sorted, [...sorted].sort())
    for (const post of first.items) {
      assert.equal('locationCode' in post, false); assert.equal('isMine' in post, false)
      assert.equal('headcount' in post.roles[0], false); assert.equal('contactLink' in post, false)
      assert.deepEqual(Object.keys(post.roles[0]).sort(), ['remaining', 'roleCode', 'roleName'])
    }
    assert.throws(() => mock.mockList('LATEST', 'unknown'), /커서/)
  })
  await check('본인·다른 사람·마감·전체 지원 상태 및 역할 마감 분기', () => {
    assert.equal(model.postActionLabel(mock.mockDetail('1', 'owner')), '지원자 보기 · 준비 중')
    assert.equal(model.postActionLabel(mock.mockDetail('1', 'other')), '지원하기 · 준비 중')
    assert.equal(model.postActionLabel(mock.mockDetail('12', 'guest')), '모집이 마감됐어요')
    for (const viewer of ['applied', 'accepted', 'rejected', 'canceled', 'applicationClosed', 'left']) {
      const post = mock.mockDetail('1', viewer)
      assert.equal(model.postActionLabel(post), model.applicationLabels[post.myApplication.status])
    }
    const post = mock.mockDetail('1', 'other')
    assert.equal(model.postActionLabel({ ...post, roles: post.roles.map(role => ({ ...role, status: 'CLOSED' })) }), '지원 가능한 역할이 없어요')
    assert.equal(mock.mockDetail('2', 'guest').event, null)
    assert.equal(model.locationLabel({ ...post, locationCode: 'CUSTOM', locationText: '실제 응답 장소' }), '실제 응답 장소')
    assert.equal('contactLink' in post, false)
  })
  await check('기존 인증·프로필 등 라우트의 정적 렌더 및 모집 화면 헤더 분리', async () => {
    const { default: App } = await server.ssrLoadModule('/src/App.tsx')
    const render = path => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(App)))
    for (const [path, title] of [
      ['/login', '로그인'], ['/signup', '회원가입'], ['/school-verification', '학교 인증'],
      ['/profile/setup', '초기 프로필 설정'], ['/me/profile', '내 프로필'],
      ['/me/profile/edit', '프로필 수정'], ['/users/1', '다른 사용자 프로필'],
      ['/posts/1/applicants', '모집글 지원자 관리'],
      ['/me/posts', '내 모집글'], ['/me/applications', '내 지원 내역'],
    ]) {
      const html = render(path)
      assert.ok(html.includes(`<h1>${title}</h1>`), path)
      assert.ok(html.includes('class="app-layout"'), path)
      assert.ok(!html.includes('class="posts-layout"'), path)
    }
    assert.ok(render('/posts').includes('class="posts-layout"'))
    assert.ok(render('/posts/1').includes('class="posts-layout"'))
    const create = render('/posts/new')
    assert.ok(create.includes('class="posts-layout"'))
    assert.ok(create.includes('<h1>모집글 작성</h1>'))
    assert.ok(create.includes('name="contactLink"'))
    assert.ok(create.includes('개발 중 · 인증 연동 대기'))
    assert.ok(!create.includes('로그인하기 (새 탭)'))
    assert.ok(!create.includes('학교 인증하기 (새 탭)'))
  })
  // 별도 Vite 모듈 그래프로 mock 모드 분기를 검사합니다. HTTP 리스너는 열지 않습니다.
  process.env.VITE_POSTS_MOCK = 'true'
  mockServer = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
  const mockSource = await mockServer.ssrLoadModule('/src/features/posts/source.ts')
  await check('mock 모드: fetch 없이 빈 목록·추가 오류·Abort 처리', async () => {
    assert.equal(mockSource.isPostMock, true)
    globalThis.fetch = async () => { throw new Error('mock에서 fetch가 호출되면 실패') }
    const signal = new AbortController().signal
    const empty = await mockSource.loadPosts('LATEST', undefined, 'empty', signal)
    assert.deepEqual(empty, { items: [], nextCursor: null, hasNext: false })
    const page = await mockSource.loadPosts('LATEST', undefined, 'moreError', signal)
    await assert.rejects(mockSource.loadPosts('LATEST', page.nextCursor, 'moreError', signal), /추가 로딩 오류/)
    const controller = new AbortController(); controller.abort()
    await assert.rejects(mockSource.loadPost('1', 'slow', controller.signal), error => error.name === 'AbortError')
    assert.equal((await mockSource.loadPost('1', 'normal', signal, 'owner')).isMine, true)
  })
  console.log(`${passed} checks passed (요청 가로채기 및 데이터 검증, 실제 서버/브라우저 검증 아님).`)
} finally {
  globalThis.fetch = originalFetch
  await mockServer?.close()
  await server.close()
}
