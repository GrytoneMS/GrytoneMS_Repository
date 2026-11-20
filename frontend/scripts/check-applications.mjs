// fetch를 가로채는 자동 검증이며 실제 백엔드 지원 성공 검증이 아닙니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
const mockMode = process.argv.includes('--mock')
process.env.VITE_POSTS_MOCK = String(mockMode)
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let count = 0
async function check(name, run) { await run(); console.log(`PASS ${++count} ${name}`) }
try {
  const model = await server.ssrLoadModule('/src/features/posts/application-model.ts')
  const { applyToPost } = await server.ssrLoadModule('/src/features/posts/application-api.ts')
  const { createApplicationSubmitter, refreshAppliedPost } = await server.ssrLoadModule('/src/features/posts/application-submit.ts')
  const { mockApplicationSender } = await server.ssrLoadModule('/src/features/posts/application-mock.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const post = { id: 7, status: 'OPEN', deadline: '2099-12-31', isMine: false, myApplication: null, roles: [
    { id: 31, roleCode: 'VIDEO', status: 'OPEN', headcount: 2, filledCount: 1 },
    { id: 32, roleCode: 'DESIGN', status: 'CLOSED', headcount: 1, filledCount: 0 },
    { id: 33, roleCode: 'DEV', status: 'OPEN', headcount: 1, filledCount: 1 },
  ] }
  const valid = { postRoleId: 31, message: '가'.repeat(20), availableTime: '평일 저녁' }
  await check('필수·공백·메시지 19/20/300/301자·시간 0/1/100/101자', () => {
    for (const [length, expected] of [[19, true], [20, false], [300, false], [301, true]]) assert.equal(!!model.validateApplication({ ...valid, message: '가'.repeat(length) }, post).message, expected)
    for (const [length, expected] of [[0, true], [1, false], [100, false], [101, true]]) assert.equal(!!model.validateApplication({ ...valid, availableTime: '가'.repeat(length) }, post).availableTime, expected)
    for (const message of ['', ' '.repeat(20), '\t\n']) assert.ok(model.validateApplication({ ...valid, message }, post).message)
    assert.ok(model.validateApplication({ ...valid, availableTime: '   ' }, post).availableTime)
    assert.deepEqual(model.validateApplication({ ...valid, message: ' ' + '가'.repeat(19) }, post), {})
    assert.deepEqual(model.validateApplication({ ...valid, message: '😀'.repeat(10) }, post), {})
  })
  await check('현재 모집글 역할 id만 허용·역할 마감·정원 도달 차단', () => {
    for (const postRoleId of [null, 0, 32, 33, 999, 'VIDEO']) assert.ok(model.validateApplication({ ...valid, postRoleId }, post).postRoleId)
    assert.deepEqual(model.validateApplication(valid, post), {})
  })
  await check('본인 글·전체 6개 지원 상태 재지원 금지·KST 마감 경계', () => {
    assert.ok(model.applicationBlock({ ...post, isMine: true }))
    for (const status of ['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'CLOSED', 'LEFT']) assert.ok(model.applicationBlock({ ...post, myApplication: { id: 55, postRoleId: 31, status } }))
    assert.ok(model.applicationBlock({ ...post, status: 'CLOSED' }))
    assert.ok(model.applicationBlock({ ...post, roles: post.roles.slice(1) }))
    assert.equal(model.applicationBlock({ ...post, deadline: '2026-11-13' }, Date.parse('2026-11-13T23:59:59+09:00')), '')
    assert.ok(model.applicationBlock({ ...post, deadline: '2026-11-13' }, Date.parse('2026-11-14T00:00:00+09:00')))
  })
  let requests = 0
  globalThis.fetch = async (url, init) => {
    requests++
    assert.equal(url, 'http://localhost:8080/api/posts/7/applications')
    assert.equal(init.method, 'POST')
    assert.equal(init.headers.get('Authorization'), 'Bearer automated-test-only')
    assert.deepEqual(JSON.parse(init.body), valid)
    return new Response(JSON.stringify({ success: true, data: { id: 99, status: 'PENDING' } }), { status: 201 })
  }
  await check('실제 API 모드에서 토큰 없으면 요청 0회·예시 실행 금지', async () => {
    await assert.rejects(applyToPost(7, valid), error => error.code === 'AUTH_PENDING')
    if (!mockMode) await assert.rejects(mockApplicationSender('success')(7, valid))
    assert.equal(requests, 0)
  })
  await check('POST 주소·숫자 postRoleId·정확한 body·Bearer·성공 응답', async () => {
    assert.deepEqual(await applyToPost(7, valid, { token: 'automated-test-only' }), { id: 99, status: 'PENDING' })
    assert.equal(requests, 1)
  })
  await check('HTTP/네트워크/응답 오류를 성공으로 바꾸지 않음', async () => {
    for (const code of ['UNAUTHORIZED', 'UNVERIFIED', 'ALREADY_APPLIED', 'ROLE_CLOSED', 'BLOCKED_USER', 'NOT_FOUND', 'SUSPENDED']) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: code }), { status: 409 })
      await assert.rejects(applyToPost(7, valid, { token: 'test-only' }), error => error.code === code)
      assert.notEqual(model.applicationFailure(new ApiError(409, { success: false, code, message: code })).message, code)
    }
    globalThis.fetch = async () => { throw new TypeError('offline') }
    await assert.rejects(applyToPost(7, valid, { token: 'test-only' }))
    globalThis.fetch = async () => new Response(JSON.stringify({ success: true, data: {} }))
    await assert.rejects(applyToPost(7, valid, { token: 'test-only' }), error => error.code === 'INVALID_RESPONSE')
  })
  await check('필드 오류의 배열·객체 형식 보존', () => {
    for (const fieldErrors of [[{ field: 'message', message: '오류' }], { message: '오류' }]) {
      assert.equal(model.applicationFailure(new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '입력 확인', fieldErrors })).fields.message, '오류')
    }
  })
  await check('동일 틱 중복 제출 방지·제출 성공 후 상태 전달', async () => {
    const submit = createApplicationSubmitter()
    let finish, sends = 0, success = 0, starts = 0
    const send = async () => { sends++; return new Promise(resolve => { finish = resolve }) }
    const callbacks = { invalid: assert.fail, start: () => starts++, success: (result, draft) => { success++; assert.equal(result.status, 'PENDING'); assert.equal(draft.postRoleId, 31) }, failure: assert.fail, settled: () => {} }
    const first = submit(post, valid, callbacks, send)
    await submit(post, valid, callbacks, send)
    assert.equal(sends, 1); assert.equal(starts, 1)
    finish({ id: 99, status: 'PENDING' }); await first
    assert.equal(success, 1)
  })
  await check('실패 시 입력 유지·재시도 가능·유효하지 않은 입력은 전송하지 않음', async () => {
    const submit = createApplicationSubmitter(), draft = { ...valid }, before = { ...draft }
    let failures = 0, successes = 0, invalids = 0
    const callbacks = { invalid: () => invalids++, start: () => {}, success: () => successes++, failure: () => failures++, settled: () => {} }
    await submit(post, draft, callbacks, async () => { throw new Error('offline') })
    assert.deepEqual(draft, before); assert.equal(failures, 1)
    await submit(post, draft, callbacks, async () => ({ id: 1, status: 'PENDING' }))
    assert.equal(successes, 1)
    await submit(post, { ...draft, message: '' }, callbacks, assert.fail)
    assert.equal(invalids, 1)
    await submit({ ...post, isMine: true }, draft, callbacks, assert.fail)
    assert.equal(failures, 2)
  })
  await check('성공 후 상세 갱신·재조회 실패/지연된 익명 응답에도 완료 상태 유지', async () => {
    const applied = { ...post, myApplication: { id: 99, postRoleId: 31, status: 'PENDING' } }
    const updated = { ...applied, status: 'CLOSED' }
    assert.deepEqual(await refreshAppliedPost(applied, async () => updated), { post: updated, refreshFailed: false })
    const failed = await refreshAppliedPost(applied, async () => { throw new Error('detail failed') })
    assert.equal(failed.post, applied); assert.equal(failed.refreshFailed, true)
    assert.ok(model.applicationBlock(failed.post))
    const delayed = await refreshAppliedPost(applied, async () => post)
    assert.deepEqual(delayed.post.myApplication, applied.myApplication)
  })
  if (mockMode) await check('명시적 개발 예시 성공·실패·실제 fetch 0회', async () => {
    globalThis.fetch = assert.fail
    assert.deepEqual(await mockApplicationSender('success')(7, valid), { id: 9001, status: 'PENDING' })
    for (const [outcome, code] of [['failure', 'INTERNAL_ERROR'], ['expired', 'UNAUTHORIZED'], ['unverified', 'UNVERIFIED'], ['duplicate', 'ALREADY_APPLIED'], ['closed', 'ROLE_CLOSED'], ['blocked', 'NOT_FOUND'], ['suspended', 'SUSPENDED']]) {
      await assert.rejects(mockApplicationSender(outcome)(7, valid), error => error.code === code)
    }
  })
} finally { globalThis.fetch = originalFetch; await server.close() }
console.log(`${count} application checks passed (intercepted fetch, no DB)`)
