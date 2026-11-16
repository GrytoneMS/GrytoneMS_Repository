// Intercepted API/controller/static render/mock checks; no real JWT, server or DB.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const config = { server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' }
const server = await createServer(config), originalFetch = globalThis.fetch
let mockServer, passed = 0
const check = async (name, run) => { await run(); console.log(`PASS ${++passed} ${name}`) }
const gate = () => { let resolve, reject; const promise = new Promise((yes,no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const page = (items, nextCursor = null) => ({ items, hasNext: nextCursor !== null, nextCursor })
const post = (id = 10, status = 'OPEN', applicants = 0) => ({ id, title: '내 모집글 <script>긴 제목</script>', category: 'PROJECT', status, deadline: '2026-12-01', dDay: 3, applicantCount: applicants, pendingCount: 0, createdAt: '2026-11-15T12:00:00', closedAt: status === 'CLOSED' ? '2026-11-15T14:00:00' : null,
  roles: [{ id: 31, roleCode: 'DEV', roleName: '아주 긴 역할명 개발', headcount: 3, filledCount: applicants ? 1 : 0, status, applicantCount: applicants, pendingCount: 0 }] })
const signal = () => new AbortController().signal
const envelope = data => new Response(JSON.stringify({ success: true, data }), { status: 200 })
try {
  const api = await server.ssrLoadModule('/src/features/my-posts/api.ts')
  const { createMyPosts } = await server.ssrLoadModule('/src/features/my-posts/controller.ts')
  const { myPostsService } = await server.ssrLoadModule('/src/features/my-posts/source.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const token = { token: 'test-only' }, failure = (code, status = 409) => new ApiError(status, { success: false, code, message: code })
  await check('무토큰·빈·공백 인증은 목록/삭제/마감 fetch 0회·실제 모드 쿼리 무시', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; assert.fail('unexpected fetch') }
    for (const options of [{}, { token: '' }, { token: '  ' }]) {
      await assert.rejects(api.getMyPosts('OPEN', undefined, options), e => e.code === 'AUTH_PENDING')
      await assert.rejects(api.deleteMyPost(10, options), e => e.code === 'AUTH_PENDING')
      const source = myPostsService(new URLSearchParams('mine=normal&result=success'), options)
      await assert.rejects(source.load('OPEN', undefined, signal()), e => e.code === 'AUTH_PENDING')
      await assert.rejects(source.close(10, signal()), e => e.code === 'AUTH_PENDING')
    }
    assert.equal(calls, 0)
  })
  await check('OPEN/CLOSED·size·Long cursor 원문·Bearer·signal·DELETE 계약', async () => {
    const calls = [], requestSignal = signal()
    globalThis.fetch = async (url, options) => { calls.push([url,options]); return envelope(options.method === 'DELETE' ? null : page([post(10, new URL(url).searchParams.get('status'))])) }
    for (const status of ['OPEN','CLOSED']) {
      await api.getMyPosts(status, '9223372036854775807', { ...token, signal: requestSignal }, 2)
      const [url,options] = calls.at(-1), params = new URL(url).searchParams
      assert.equal(new URL(url).pathname, '/api/users/me/posts'); assert.equal(params.get('status'), status); assert.equal(params.get('cursor'), '9223372036854775807'); assert.equal(params.get('size'), '2')
      assert.equal(options.headers.get('Authorization'), 'Bearer test-only'); assert.equal(options.signal, requestSignal)
    }
    await api.deleteMyPost(10, token); assert.equal(calls.at(-1)[0], 'http://localhost:8080/api/posts/10'); assert.equal(calls.at(-1)[1].method, 'DELETE'); assert.equal(calls.at(-1)[1].body, undefined)
    await assert.rejects(api.getMyPosts('OPEN', '9223372036854775808', token))
  })
  await check('응답 페이지·카운트·상태·중복 id·잘못된 마감/삭제 응답 거부', async () => {
    for (const value of [null, {}, page([post(),post()]), page([post(10,'CLOSED')]), { ...page([]), hasNext: true }, page([{ ...post(), applicantCount: 2 }]), page([{ ...post(), roles: [{ ...post().roles[0], pendingCount: 3 }] }])]) {
      globalThis.fetch = async () => envelope(value)
      await assert.rejects(api.getMyPosts('OPEN',undefined,token), e => e.code === 'INVALID_RESPONSE')
    }
    globalThis.fetch = async () => envelope({})
    await assert.rejects(api.deleteMyPost(10,token), e => e.code === 'INVALID_RESPONSE')
  })
  await check('실제 API 401/403/404/409/500·네트워크 실패를 mock 성공으로 바꾸지 않음', async () => {
    for (const [status,code] of [[401,'UNAUTHORIZED'],[403,'FORBIDDEN'],[404,'NOT_FOUND'],[409,'POST_HAS_APPLICANTS'],[500,'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({success:false,code,message:code}),{status})
      await assert.rejects(api.getMyPosts('OPEN',undefined,token), e => e.code === code)
      await assert.rejects(api.deleteMyPost(10,token), e => e.code === code)
    }
    globalThis.fetch = async () => { throw new TypeError('offline') }; await assert.rejects(api.getMyPosts('OPEN',undefined,token))
  })
  await check('탭 변경 목록·커서 초기화·abort 무시한 이전 응답/오류 폐기', async () => {
    for (const stale of ['success','error']) {
    const old = gate(), next = gate(), calls = []
    const c = createMyPosts({ load: (status,cursor,requestSignal) => { calls.push({status,cursor,requestSignal}); return status === 'OPEN' ? old.promise : next.promise }, close: assert.fail, remove: assert.fail })
    const pending = c.load(); c.tab('CLOSED')
    assert.equal(c.getSnapshot().items.length,0); assert.equal(c.getSnapshot().cursor,null); assert.ok(calls[0].requestSignal.aborted); assert.equal(calls[1].cursor,undefined)
    next.resolve(page([post(11,'CLOSED')])); await new Promise(resolve => setImmediate(resolve))
    if (stale === 'error') old.reject(new Error('old')); else old.resolve(page([post(10)],'10'))
    await pending
    assert.equal(c.getSnapshot().status,'CLOSED'); assert.equal(c.getSnapshot().items[0].id,11); assert.equal(c.getSnapshot().error,''); c.dispose()
    }
  })
  await check('더 보기 중복 요청 방지·중복 id 제거·실패 시 목록 유지·재시도', async () => {
    let calls = 0, fail = true; const second = gate()
    const c = createMyPosts({ load: async (_status,cursor) => { calls++; if (!cursor) return page([post(10)],'10'); if(fail) { await second.promise; throw new Error('more') } return page([post(10),post(9)]) }, close: assert.fail, remove: assert.fail })
    await c.load(); const more = c.load(true); await c.load(true); assert.equal(calls,2); second.resolve(); await more
    assert.equal(c.getSnapshot().items.length,1); assert.ok(c.getSnapshot().moreError)
    fail = false; await c.load(true); assert.deepEqual(c.getSnapshot().items.map(x=>x.id),[10,9]); assert.equal(c.getSnapshot().hasNext,false); c.dispose()
  })
  await check('삭제 조건은 전체 applicantCount·마감된 글 모순 문구 없음·pendingCount와 구분', () => {
    assert.equal(api.canDelete(post()),true); assert.equal(api.canDelete(post(10,'OPEN',2)),false)
    assert.ok(!api.deletionNote(post(10,'CLOSED',2)).includes('대신 마감')); assert.ok(api.deletionNote(post(10,'OPEN',2)).includes('대신 마감'))
  })
  for (const kind of ['close','delete']) await check(`${kind}: 동일 틱 중복 방지·성공 후 서버 첫 페이지 재조회·마감 재요청 금지`, async () => {
    let calls = 0, loads = 0, live = page([post()]); const pending = gate()
    const act = async () => { calls++; await pending.promise; live = page([]) }
    const c = createMyPosts({load:async () => {loads++; return live}, close:act, remove:act})
    await c.load(); const first = c.act(10,kind); await c.act(10,kind); await c.load(); assert.equal(calls,1)
    pending.resolve(); await first; assert.equal(loads,2); assert.equal(c.getSnapshot().items.length,0); assert.ok(c.getSnapshot().notice); assert.equal(c.getSnapshot().needsRefresh,false)
    await c.act(10,kind); assert.equal(calls,1); c.dispose()
  })
  await check('성공 후 갱신 실패와 stale OPEN 분리·처리 완료 유지·목록만 재시도', async () => {
    for (const kind of ['close','delete']) {
      let loads=0, calls=0, fresh=false
      const mutate = async () => {calls++}
      const c = createMyPosts({load:async () => { loads++; if(loads>1 && !fresh) return page([post()]); return loads === 1 ? page([post()]) : page([])}, close:mutate,remove:mutate})
      await c.load(); await c.act(10,kind)
      assert.ok(c.getSnapshot().notice)
      assert.ok(c.getSnapshot().error.includes('완료')); assert.equal(c.getSnapshot().needsRefresh,true); assert.equal(c.getSnapshot().items.length,0)
      await c.act(10,kind); assert.equal(calls,1); fresh=true; await c.load(); assert.equal(c.getSnapshot().needsRefresh,false); c.dispose()
    }
  })
  await check('실패·지원자 존재·불명확 응답 잠금과 조회 재시도·폐기 후 늦은 처리 무시', async () => {
    for (const error of [failure('POST_HAS_APPLICANTS'),new TypeError('offline'),failure('INTERNAL_ERROR',500)]) {
      const c = createMyPosts({load:async()=>page([post()]),close:async()=>{throw error},remove:async()=>{throw error}})
      await c.load(); await c.act(10,'delete'); assert.equal(c.getSnapshot().notice,''); assert.equal(c.getSnapshot().needsRefresh,true)
      if(error.code==='POST_HAS_APPLICANTS') assert.ok(c.getSnapshot().error.includes('지원자가 있어'))
      await c.load(); assert.equal(c.getSnapshot().needsRefresh,false); c.dispose()
    }
    const pending=gate(), c=createMyPosts({load:async()=>page([post()]),close:()=>pending.promise,remove:assert.fail})
    await c.load(); const first=c.act(10,'close'); c.dispose(); const state=c.getSnapshot(); pending.resolve(); await first; assert.equal(c.getSnapshot(),state)
  })
  await check('카드 정적 렌더: 실제 역할 카운트·정확한 링크·수정 비활성·연락처 UI 없음', async () => {
    const { MyPostCard } = await server.ssrLoadModule('/src/pages/MyPostsPage.tsx')
    const html=renderToStaticMarkup(createElement(MemoryRouter,null,createElement(MyPostCard,{post:post(10,'OPEN',2),search:'',locked:false,busy:false,onAction(){}})))
    assert.ok(html.includes('지원 2')); assert.ok(html.includes('수락 1/3')); assert.ok(html.includes('/posts/10/applicants')); assert.ok(html.includes('href="/posts/10"')); assert.ok(html.includes('수정 · 준비 중')); assert.ok(!html.includes('연락 수단')); assert.ok(html.includes('&lt;script&gt;'))
    assert.ok(!html.includes('>삭제</button>'))
  })
  process.env.VITE_POSTS_MOCK='true'; mockServer=await createServer(config)
  const {myPostsService: examples}=await mockServer.ssrLoadModule('/src/features/my-posts/source.ts')
  await check('명시적 mock: 두 탭·여러 역할·지원자 있음/없음·빈/오류/더 보기·structuredClone 미사용', async () => {
    globalThis.fetch=assert.fail; const originalClone=globalThis.structuredClone; globalThis.structuredClone=undefined
    try {
      const service=examples(new URLSearchParams())
      for(const status of ['OPEN','CLOSED']) { const value=await service.load(status,undefined,signal()); api.validateMyPage(value,status); assert.ok(value.items.some(api.canDelete)); assert.ok(value.items.some(x=>!api.canDelete(x))) }
      const first=await service.load('OPEN',undefined,signal()); assert.equal(first.hasNext,true); assert.equal((await service.load('OPEN',first.nextCursor,signal())).items.length,3)
      first.items[0].roles[0].roleName='changed'; assert.notEqual((await service.load('OPEN',undefined,signal())).items[0].roles[0].roleName,'changed')
      assert.equal((await examples(new URLSearchParams('mine=empty')).load('OPEN',undefined,signal())).items.length,0)
      await assert.rejects(examples(new URLSearchParams('mine=error')).load('OPEN',undefined,signal()))
    } finally {globalThis.structuredClone=originalClone}
  })
  await check('mock 마감/삭제 성공·실패·지원자 존재·성공 후 재조회 실패', async () => {
    for (const kind of ['close','remove']) for(const outcome of ['success','failure','refreshFailure','hasApplicants']) {
      const service=examples(new URLSearchParams(`result=${outcome}`)), list=await service.load('OPEN',undefined,signal()), target=list.items.find(api.canDelete)
      if(outcome==='failure'||kind==='remove'&&outcome==='hasApplicants') {await assert.rejects(service[kind](target.id,signal()));continue}
      await service[kind](target.id,signal())
      if(outcome==='refreshFailure') await assert.rejects(service.load('OPEN',undefined,signal()))
      else assert.ok(!(await service.load('OPEN',undefined,signal())).items.some(x=>x.id===target.id))
    }
  })
  console.log(`${passed} my post checks passed (no real server/DB/browser).`)
} finally {globalThis.fetch=originalFetch; await mockServer?.close(); await server.close()}
