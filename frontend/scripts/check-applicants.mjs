// 요청 가로채기·상태 전이·정적 렌더 검증입니다. 실제 JWT/백엔드/브라우저 검증이 아닙니다.
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
async function check(name, run) { await run(); console.log(`PASS ${++passed} ${name}`) }
const clone = value => structuredClone(value)
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
try {
  const api = await server.ssrLoadModule('/src/features/applicants/api.ts')
  const model = await server.ssrLoadModule('/src/features/applicants/model.ts')
  const { createManagementController, initialManagementState } = await server.ssrLoadModule('/src/features/applicants/controller.ts')
  const { managementService } = await server.ssrLoadModule('/src/features/applicants/source.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { mockDetail } = await server.ssrLoadModule('/src/features/posts/mock.ts')
  const post = clone(mockDetail('1', 'owner'))
  const application = (id, status = 'PENDING') => ({ id, status, message: '<script>입력 텍스트</script>', availableTime: '평일 저녁\n주말', createdAt: '2026-11-13T10:00:00', applicant: { id: 201 + id, nickname: `지원자 ${id}`, schoolName: '학교', major: null, grade: null, profileImageUrl: null } })
  const snapshot = { post, roles: post.roles.map((role, index) => ({ postRoleId: role.id, roleName: role.roleName, headcount: role.headcount, filledCount: role.filledCount, applications: index === 0 ? [application(501), application(502), application(503, 'REJECTED')] : [application(504, 'ACCEPTED')] })) }
  const accepted = { applicationId: 501, status: 'ACCEPTED', roleClosed: true, postClosed: false }
  const rejected = { applicationId: 501, status: 'REJECTED' }
  const token = { token: 'automated-test-only' }
  const envelope = data => new Response(JSON.stringify({ success: true, data }), { status: 200 })
  const failure = (status, code) => new ApiError(status, { success: false, code, message: code })

  await check('관리 조회·수락·거절: 누락/공백 토큰이면 fetch 0회·실제 모드 쿼리 무시', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; assert.fail('토큰 없는 요청') }
    for (const options of [{}, { token: '' }, { token: '   ' }]) {
      await assert.rejects(api.getApplicants('1', options), error => error.code === 'AUTH_PENDING')
      await assert.rejects(api.loadManagement('1', options), error => error.code === 'AUTH_PENDING')
      for (const decision of ['accept', 'reject']) await assert.rejects(api.decideApplication(501, decision, null, options), error => error.code === 'AUTH_PENDING')
    }
    const service = managementService('1', new URLSearchParams('viewer=owner&result=success&manage=normal'))
    await assert.rejects(service.load(new AbortController().signal), error => error.code === 'AUTH_PENDING')
    await assert.rejects(service.decide(501, 'accept', null), error => error.code === 'AUTH_PENDING')
    assert.equal(calls, 0)
  })
  await check('정확한 GET post id·PATCH application id·Bearer/signal·선택 거절 사유', async () => {
    const calls = [], signal = new AbortController().signal
    globalThis.fetch = async (url, options) => {
      calls.push([url, options]); assert.equal(options.headers.get('Authorization'), 'Bearer automated-test-only')
      if (url.endsWith('/accept')) return envelope(accepted)
      if (url.endsWith('/reject')) return envelope(rejected)
      return envelope(snapshot.roles)
    }
    assert.deepEqual(await api.getApplicants('1', { ...token, signal }), snapshot.roles)
    assert.equal(calls[0][0], 'http://localhost:8080/api/posts/1/applications'); assert.equal(calls[0][1].signal, signal)
    assert.deepEqual(await api.decideApplication(501, 'accept', null, token), accepted)
    assert.equal(calls[1][0], 'http://localhost:8080/api/applications/501/accept'); assert.equal(calls[1][1].method, 'PATCH'); assert.equal(calls[1][1].body, undefined)
    for (const reason of [null, 'ROLE_MISMATCH', 'SCHEDULE', 'FILLED', 'ETC']) {
      await api.decideApplication(501, 'reject', reason, token)
      assert.equal(calls.at(-1)[0], 'http://localhost:8080/api/applications/501/reject')
      assert.equal(calls.at(-1)[1].method, 'PATCH'); assert.deepEqual(JSON.parse(calls.at(-1)[1].body), { reason })
    }
    await assert.rejects(api.decideApplication(0, 'accept', null, token), error => error.code === 'INVALID_INPUT')
    await assert.rejects(api.decideApplication(501, 'reject', 'free text', token), error => error.code === 'INVALID_INPUT')
    for (const id of ['0', '../1', 'example-post', '9223372036854775808']) assert.throws(() => api.checkPostId(id))
  })
  await check('관리 조회: 팀장 확인 뒤 목록 요청·모집 현황 불일치/비팀장 차단', async () => {
    const calls = []
    globalThis.fetch = async (url, options) => { calls.push(url); assert.equal(options.headers.get('Authorization'), 'Bearer automated-test-only'); return envelope(url.endsWith('/applications') ? snapshot.roles : post) }
    assert.deepEqual(await api.loadManagement('1', token), snapshot)
    assert.deepEqual(calls, ['http://localhost:8080/api/posts/1', 'http://localhost:8080/api/posts/1/applications'])
    globalThis.fetch = async () => envelope({ ...post, isMine: false })
    await assert.rejects(api.loadManagement('1', token), error => error.code === 'FORBIDDEN')
    globalThis.fetch = async url => envelope(url.endsWith('/applications') ? [{ ...snapshot.roles[0], filledCount: 99 }] : post)
    await assert.rejects(api.loadManagement('1', token), error => error.code === 'INVALID_RESPONSE')
  })
  await check('잘못된 응답·중복 지원 id·상태·수락 응답 id를 성공 처리하지 않음', async () => {
    const malformed = [null, {}, [{ ...snapshot.roles[0], applications: [application(501), application(501)] }], [{ ...snapshot.roles[0], applications: [application(501, 'UNKNOWN')] }]]
    for (const value of malformed) {
      globalThis.fetch = async () => envelope(value)
      await assert.rejects(api.getApplicants('1', token), error => error.code === 'INVALID_RESPONSE')
    }
    for (const value of [{ ...accepted, applicationId: 999 }, { ...accepted, roleClosed: undefined }, { applicationId: 501, status: 'PENDING' }]) {
      globalThis.fetch = async () => envelope(value)
      await assert.rejects(api.decideApplication(501, 'accept', null, token), error => error.code === 'INVALID_RESPONSE')
    }
  })
  await check('401·403·404·정원·이미 처리·500·네트워크 오류 전파, mock 성공 전환 없음', async () => {
    for (const [status, code] of [[401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'NOT_FOUND'], [409, 'ROLE_FULL'], [409, 'INVALID_APPLICATION_STATUS'], [500, 'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: code }), { status })
      await assert.rejects(api.decideApplication(501, 'accept', null, token), error => error.code === code)
      await assert.rejects(api.getApplicants('1', token), error => error.code === code)
      const shown = model.managementFailure(failure(status, code))
      assert.equal(shown.access, [401, 403, 404].includes(status))
    }
    globalThis.fetch = async () => { throw new TypeError('offline') }
    await assert.rejects(api.decideApplication(501, 'reject', null, token))
    assert.equal(model.managementFailure(new TypeError('offline')).reconcile, true)
  })
  await check('6개 상태별 버튼 규칙·본인 아님·정원/역할 마감·글 마감 규칙', () => {
    for (const status of Object.keys(model.statusLabels)) {
      const value = clone(snapshot); value.roles[0].applications[0].status = status
      for (const decision of ['accept', 'reject']) assert.equal(!!model.decisionBlock(value, 501, decision), status !== 'PENDING')
    }
    const full = clone(snapshot); full.roles[0].filledCount = full.roles[0].headcount
    assert.ok(model.decisionBlock(full, 501, 'accept')); assert.equal(model.decisionBlock(full, 501, 'reject'), '')
    const closedRole = clone(snapshot); closedRole.post.roles[0].status = 'CLOSED'
    assert.ok(model.decisionBlock(closedRole, 501, 'accept')); assert.equal(model.decisionBlock(closedRole, 501, 'reject'), '')
    const closedPost = clone(snapshot); closedPost.post.status = 'CLOSED'; closedPost.post.deadline = '2020-01-01'
    assert.equal(model.decisionBlock(closedPost, 501, 'accept'), ''); assert.equal(model.decisionBlock(closedPost, 501, 'reject'), '')
    assert.ok(model.decisionBlock({ ...snapshot, post: { ...post, isMine: false } }, 501, 'accept'))
    assert.ok(model.decisionBlock(snapshot, 999, 'reject'))
  })
  await check('수락 상태·선발 인원·역할/글 자동 마감 반영·원본 불변', () => {
    const before = clone(snapshot)
    const result = model.applyDecision(snapshot, { ...accepted, postClosed: true })
    assert.equal(result.roles[0].applications[0].status, 'ACCEPTED'); assert.equal(result.roles[0].applications[1].status, 'CLOSED')
    assert.equal(result.roles[0].applications[2].status, 'REJECTED'); assert.equal(result.roles[0].filledCount, 1)
    assert.equal(result.post.roles[0].status, 'CLOSED'); assert.equal(result.post.status, 'CLOSED')
    assert.deepEqual(snapshot, before)
    const reject = model.applyDecision(snapshot, rejected)
    assert.equal(reject.roles[0].filledCount, 0); assert.equal(reject.roles[0].applications[1].status, 'PENDING')
  })
  await check('같은 틱 중복 수락/거절 차단·성공 후 목록과 모집 현황 갱신', async () => {
    let state, calls = 0, loads = 0, live = clone(snapshot)
    const gate = deferred()
    const controller = createManagementController({ load: async () => { loads++; return clone(live) }, decide: async id => { calls++; assert.equal(id, 501); await gate.promise; live = model.applyDecision(live, accepted); return accepted } }, next => { state = next })
    await controller.load()
    const first = controller.decide(501, 'accept', null)
    await controller.decide(501, 'accept', null); await controller.decide(502, 'reject', 'SCHEDULE'); await controller.load()
    assert.equal(calls, 1); assert.equal(loads, 1); assert.equal(state.busy, true)
    gate.resolve(); await first
    assert.equal(loads, 2); assert.equal(state.snapshot.roles[0].filledCount, 1); assert.equal(state.snapshot.roles[0].applications[0].status, 'ACCEPTED')
    assert.equal(state.busy, false); assert.equal(state.needsRefresh, false); assert.ok(state.notice.includes('완료'))
    await controller.decide(501, 'accept', null); assert.equal(calls, 1)
    controller.dispose()
  })
  await check('성공 후 조회 실패: 완료 상태 유지·재처리 금지·조회만 재시도', async () => {
    let state, failRefresh = false, calls = 0, live = clone(snapshot)
    const controller = createManagementController({ load: async () => { if (failRefresh) throw new Error('refresh failed'); return clone(live) }, decide: async () => { calls++; live = model.applyDecision(live, rejected); failRefresh = true; return rejected } }, next => { state = next })
    await controller.load(); await controller.decide(501, 'reject', 'SCHEDULE')
    assert.equal(state.snapshot.roles[0].applications[0].status, 'REJECTED'); assert.ok(state.notice.includes('거절')); assert.equal(state.needsRefresh, true)
    await controller.decide(501, 'reject', null); await controller.decide(502, 'accept', null); assert.equal(calls, 1)
    failRefresh = false; await controller.load()
    assert.equal(state.needsRefresh, false); assert.equal(calls, 1); controller.dispose()
  })
  await check('지연된 조회가 성공 상태를 PENDING으로 되돌리지 않음', async () => {
    let state
    const controller = createManagementController({ load: async () => clone(snapshot), decide: async () => rejected }, next => { state = next })
    await controller.load(); await controller.decide(501, 'reject', null)
    assert.equal(state.snapshot.roles[0].applications[0].status, 'REJECTED'); assert.equal(state.needsRefresh, true); controller.dispose()
  })
  await check('처리 오류: 성공 안내 없음·원본 유지·정원/네트워크는 갱신 전 잠금', async () => {
    for (const error of [failure(409, 'ROLE_FULL'), failure(409, 'INVALID_APPLICATION_STATUS'), failure(500, 'INTERNAL_ERROR'), new TypeError('offline')]) {
      let state
      const controller = createManagementController({ load: async () => clone(snapshot), decide: async () => { throw error } }, next => { state = next })
      await controller.load(); await controller.decide(501, 'accept', null)
      assert.equal(state.notice, ''); assert.deepEqual(state.snapshot, snapshot); assert.equal(state.needsRefresh, true); assert.equal(state.busy, false)
      await controller.load(); assert.equal(state.needsRefresh, false); controller.dispose()
    }
  })
  await check('권한 오류는 기존 지원자 정보 숨김·늦은 조회/화면 이탈 결과 무시', async () => {
    let state
    const controller = createManagementController({ load: async () => clone(snapshot), decide: async () => { throw failure(403, 'FORBIDDEN') } }, next => { state = next })
    await controller.load(); await controller.decide(501, 'reject', null); assert.equal(state.snapshot, null); controller.dispose()
    const first = deferred(), second = deferred(); let calls = 0
    const racing = createManagementController({ load: () => ++calls === 1 ? first.promise : second.promise, decide: assert.fail }, next => { state = next })
    const one = racing.load(), two = racing.load()
    second.resolve(snapshot); await two; first.reject(new Error('old error')); await one
    assert.equal(state.error, null); assert.deepEqual(state.snapshot, snapshot)
    const before = state; racing.dispose(); await racing.load(); assert.equal(state, before)
    const gate = deferred(); let emissions = 0
    const leaving = createManagementController({ load: async () => clone(snapshot), decide: () => gate.promise }, () => emissions++)
    await leaving.load(); const pending = leaving.decide(501, 'accept', null); leaving.dispose(); const emitted = emissions
    gate.resolve(accepted); await pending; assert.equal(emissions, emitted)
  })
  await check('정적 렌더: 실제 정보·프로필 id·입력 이스케이프·처리 잠금', async () => {
    const { ApplicantsView } = await server.ssrLoadModule('/src/features/applicants/ApplicantsView.tsx')
    const render = locked => renderToStaticMarkup(createElement(MemoryRouter, null, createElement(ApplicantsView, { snapshot, state: { ...initialManagementState(), snapshot, needsRefresh: locked }, reason: null, onReason() {}, onAction() {}, onRefresh() {}, search: '' })))
    const html = render(false)
    assert.ok(html.includes('/users/702')); assert.ok(html.includes('가능 시간: 평일 저녁')); assert.ok(html.includes('선발 0 / 모집 1명'))
    assert.ok(html.includes('&lt;script&gt;')); assert.ok(!html.includes('<script>입력'))
    assert.ok(html.includes('aria-pressed="true"')); assert.ok(!html.includes('Premiere Pro')); assert.ok(!html.includes('인증 배지'))
    const locked = render(true)
    for (const article of locked.matchAll(/<article\b[\s\S]*?<\/article>/g)) {
      for (const button of article[0].matchAll(/<button\b[^>]*>/g)) assert.ok(button[0].includes('disabled'))
    }
  })
  process.env.VITE_POSTS_MOCK = 'true'
  mockServer = await createServer(config)
  const { managementService: mockService } = await mockServer.ssrLoadModule('/src/features/applicants/source.ts')
  await check('개발 예시: 여러 역할·상태·빈 목록·다른 사용자/비회원·조회 실패', async () => {
    globalThis.fetch = assert.fail
    const signal = new AbortController().signal
    const normal = await mockService('1', new URLSearchParams()).load(signal)
    assert.equal(normal.roles.length, 2)
    assert.deepEqual([...new Set(normal.roles.flatMap(role => role.applications.map(item => item.status)))].sort(), ['ACCEPTED', 'CLOSED', 'LEFT', 'PENDING', 'REJECTED'])
    assert.ok((await mockService('1', new URLSearchParams('manage=empty')).load(signal)).roles.every(role => role.applications.length === 0))
    for (const [query, code] of [['viewer=other', 'FORBIDDEN'], ['viewer=guest', 'UNAUTHORIZED'], ['manage=notFound', 'NOT_FOUND'], ['manage=error', 'INTERNAL_ERROR']]) await assert.rejects(mockService('1', new URLSearchParams(query)).load(signal), error => error.code === code)
    const aborted = new AbortController(); aborted.abort(); await assert.rejects(mockService('1', new URLSearchParams()).load(aborted.signal), error => error.name === 'AbortError')
  })
  await check('개발 예시: 수락/거절 성공·역할 자동 마감·각 오류·성공 후 조회 실패', async () => {
    const signal = new AbortController().signal
    const acceptService = mockService('1', new URLSearchParams()), rejectService = mockService('1', new URLSearchParams())
    await acceptService.load(signal); assert.equal((await acceptService.decide(501, 'accept', null)).roleClosed, true)
    const updated = await acceptService.load(signal)
    assert.equal(updated.roles[0].filledCount, 1); assert.equal(updated.roles[0].applications[1].status, 'CLOSED')
    await rejectService.load(signal); assert.equal((await rejectService.decide(501, 'reject', 'ETC')).status, 'REJECTED')
    for (const [result, code] of [['failure', 'INTERNAL_ERROR'], ['full', 'ROLE_FULL'], ['processed', 'INVALID_APPLICATION_STATUS'], ['expired', 'UNAUTHORIZED'], ['forbidden', 'FORBIDDEN'], ['notFound', 'NOT_FOUND']]) {
      const service = mockService('1', new URLSearchParams(`result=${result}`)); await service.load(signal)
      await assert.rejects(service.decide(501, 'accept', null), error => error.code === code)
    }
    const service = mockService('1', new URLSearchParams('result=refreshFailure')); await service.load(signal)
    assert.equal((await service.decide(501, 'reject', null)).status, 'REJECTED')
    await assert.rejects(service.load(signal), error => error.code === 'INTERNAL_ERROR')
  })
  console.log(`${passed} applicant management checks passed (no real API/DB/browser)`)
} finally { globalThis.fetch = originalFetch; await mockServer?.close(); await server.close() }
