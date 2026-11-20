// 요청을 가로채는 검증입니다. 실제 서버·JWT·CORS·브라우저 검증이 아닙니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'true' // 조회 mock 설정이 작성 요청에 영향을 주지 않는지 확인
const originalFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let passed = 0
async function check(name, run) { await run(); passed++; console.log(`PASS ${name}`) }
const today = '2026-11-11'
try {
  const model = await server.ssrLoadModule('/src/features/posts/create-model.ts')
  const { createPost } = await server.ssrLoadModule('/src/features/posts/create-api.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const { createPostSubmitter } = await server.ssrLoadModule('/src/features/posts/create-submit.ts')
  const { PostCreateAuthPendingError, postCreateAuthPendingMessage } = await server.ssrLoadModule('/src/features/posts/create-integration.ts')
  const authenticated = { token: 'test-token' } // 테스트에만 명시적으로 전달합니다. 화면에는 공급/저장하지 않습니다.
  function valid() {
    return { ...model.initialPostDraft(), title: '모집글 제목', category: 'PROJECT', description: '함께 프로젝트를 만들어갈 대학생 팀원을 모집합니다.',
      periodStart: '2026-11-20', periodEnd: '2026-12-20', deadline: today, meetingType: 'ONLINE',
      roles: [{ ...model.emptyRole(0), roleCode: 'DEV', roleName: '프론트엔드 개발' }], contactLink: 'https://open.kakao.com/example' }
  }
  await check('필수값·정상 입력 및 오늘 마감·같은 날 시작/종료 허용', () => {
    assert.ok(Object.keys(model.validatePostDraft(model.initialPostDraft(), today)).length >= 10)
    assert.deepEqual(model.validatePostDraft(valid(), today), {})
    assert.deepEqual(model.validatePostDraft({ ...valid(), periodStart: today, periodEnd: today }, today), {})
  })
  await check('제목·소개·역할·요구 조건·연락 링크 길이 경계', () => {
    for (const [field, min, max] of [['title', 5, 40], ['description', 20, 1000]]) {
      for (const length of [min, max]) assert.equal(model.validatePostDraft({ ...valid(), [field]: '가'.repeat(length) }, today)[field], undefined)
      for (const value of [' '.repeat(min), '가'.repeat(min - 1), '가'.repeat(max + 1)]) assert.ok(model.validatePostDraft({ ...valid(), [field]: value }, today)[field])
    }
    for (const [field, length] of [['roleName', 30], ['requirements', 100]]) {
      const draft = valid(); draft.roles[0][field] = '가'.repeat(length)
      assert.equal(model.validatePostDraft(draft, today)[`roles[0].${field}`], undefined)
      draft.roles[0][field] += '가'; assert.ok(model.validatePostDraft(draft, today)[`roles[0].${field}`])
    }
    for (const length of [500, 501]) {
      const value = 'https://example.com/' + 'a'.repeat(length - 20)
      // 정확한 총 길이로 작성
      const draft = { ...valid(), contactLink: value.padEnd(length, 'a') }
      assert.equal(draft.contactLink.length, length)
      assert.equal(!!model.validatePostDraft(draft, today).contactLink, length > 500)
    }
  })
  await check('모든 날짜 형식·윤일·마감/활동 순서 검증', () => {
    for (const field of ['periodStart', 'periodEnd', 'deadline']) {
      for (const value of ['', 'invalid', '2026-02-30', '2026-2-3', '0000-01-01']) assert.ok(model.validatePostDraft({ ...valid(), [field]: value }, today)[field])
    }
    assert.ok(model.validatePostDraft({ ...valid(), deadline: '2026-11-10' }, today).deadline)
    assert.ok(model.validatePostDraft({ ...valid(), deadline: '2026-11-21' }, today).deadline)
    assert.ok(model.validatePostDraft({ ...valid(), periodEnd: '2026-11-19' }, today).periodEnd)
    assert.deepEqual(model.validatePostDraft({ ...valid(), periodStart: '2028-02-29', periodEnd: '2028-02-29' }, today), {})
  })
  await check('역할 1~5개·분류·이름·정수 인원 및 선택 요구 조건', () => {
    for (const count of [0, 6]) assert.ok(model.validatePostDraft({ ...valid(), roles: Array.from({ length: count }, () => valid().roles[0]) }, today).roles)
    assert.deepEqual(model.validatePostDraft({ ...valid(), roles: Array.from({ length: 5 }, () => valid().roles[0]) }, today), {})
    for (const headcount of ['', '0', '6', '1.5', '-1', 'NaN', '1e0']) {
      const draft = valid(); draft.roles[0].headcount = headcount; assert.ok(model.validatePostDraft(draft, today)['roles[0].headcount'])
    }
    for (const count of ['1', '5']) {
      const draft = valid(); draft.roles[0].headcount = count; assert.deepEqual(model.validatePostDraft(draft, today), {})
    }
    const draft = valid(); draft.roles[0].roleCode = ''; draft.roles[0].roleName = ' '
    assert.ok(model.validatePostDraft(draft, today)['roles[0].roleCode']); assert.ok(model.validatePostDraft(draft, today)['roles[0].roleName'])
    for (const requirements of ['', '   ', '실제 조건']) {
      const draft = valid(); draft.roles[0].requirements = requirements
      assert.deepEqual(model.validatePostDraft(draft, today), {})
      assert.equal(model.toCreatePostRequest(draft).roles[0].requirements, requirements.trim() ? requirements : null)
    }
  })
  await check('대면·혼합 장소·빈도·캠퍼스/직접 장소 필수 및 길이', () => {
    for (const meetingType of ['OFFLINE', 'HYBRID']) {
      const draft = { ...valid(), meetingType }
      assert.ok(model.validatePostDraft(draft, today).locationCode); assert.ok(model.validatePostDraft(draft, today).meetingFreq)
      for (const locationCode of ['CHEONAN_TERMINAL', 'CHEONAN_ASAN_STN', 'DUJEONG_STN', 'ONYANG_STN', 'CAMPUS', 'CUSTOM']) {
        draft.locationCode = locationCode; draft.meetingFreq = '주 1회'; draft.locationText = ''
        assert.equal(!!model.validatePostDraft(draft, today).locationText, ['CAMPUS', 'CUSTOM'].includes(locationCode))
      }
      draft.locationCode = 'CUSTOM'; draft.locationText = '가'.repeat(100); draft.meetingFreq = '가'.repeat(30)
      assert.deepEqual(model.validatePostDraft(draft, today), {})
      draft.locationText += '가'; draft.meetingFreq += '가'
      assert.ok(model.validatePostDraft(draft, today).locationText); assert.ok(model.validatePostDraft(draft, today).meetingFreq)
    }
  })
  await check('공모전·대회 행사 필수·길이·URL·날짜, 프로젝트·스터디 행사 미전송', () => {
    for (const category of ['CONTEST', 'COMPETITION']) {
      const draft = { ...valid(), category }
      for (const field of ['name', 'host', 'officialUrl', 'applyDeadline']) assert.ok(model.validatePostDraft(draft, today)[`event.${field}`])
      draft.event = { name: '가'.repeat(100), host: '가'.repeat(100), officialUrl: 'https://example.com', applyDeadline: today }
      assert.deepEqual(model.validatePostDraft(draft, today), {})
      draft.event.name += '가'; draft.event.host += '가'; draft.event.officialUrl = 'a'.repeat(501); draft.event.applyDeadline = '2026-02-30'
      for (const field of ['name', 'host', 'officialUrl', 'applyDeadline']) assert.ok(model.validatePostDraft(draft, today)[`event.${field}`])
    }
    for (const category of ['PROJECT', 'STUDY']) assert.equal(model.toCreatePostRequest({ ...valid(), category }).event, null)
    for (const url of ['javascript:alert(1)', 'ftp://example.com', 'example.com', 'https://']) {
      assert.ok(model.validatePostDraft({ ...valid(), contactLink: url }, today).contactLink)
      const draft = { ...valid(), category: 'CONTEST', event: { name: '행사', host: '주최', officialUrl: url, applyDeadline: today } }
      assert.ok(model.validatePostDraft(draft, today)['event.officialUrl'])
    }
  })
  await check('숨긴 조건부 값·UI key 제외, 원본 입력 보존 및 JSON 계약', () => {
    const draft = { ...valid(), locationCode: 'CUSTOM', locationText: '보존할 장소', meetingFreq: '보존할 빈도', event: { name: '보존할 행사', host: '', officialUrl: '', applyDeadline: '' } }
    const before = structuredClone(draft)
    const body = model.toCreatePostRequest(draft)
    assert.equal(body.locationCode, null); assert.equal(body.locationText, null); assert.equal(body.meetingFreq, null); assert.equal(body.event, null)
    assert.equal('key' in body.roles[0], false); assert.equal(body.roles[0].headcount, 1)
    assert.deepEqual(draft, before)
  })
  await check('실제 POST 요청·반환 id·명시 토큰/취소 전달, 조회 mock에도 생성 요청 유지', async () => {
    const calls = []
    globalThis.fetch = async (url, options) => { calls.push({ url, options }); return new Response(JSON.stringify({ success: true, data: { id: 42 } }), { status: 201 }) }
    const body = model.toCreatePostRequest(valid()); const controller = new AbortController()
    assert.deepEqual(await createPost(body, { token: 'test-token', signal: controller.signal }), { id: 42 })
    const call = calls[0]
    assert.equal(call.url, 'http://localhost:8080/api/posts'); assert.equal(call.options.method, 'POST')
    assert.deepEqual(JSON.parse(call.options.body), body); assert.equal(call.options.headers.get('Authorization'), 'Bearer test-token')
    assert.equal(call.options.headers.has('X-User-Id'), false); assert.equal(call.options.signal, controller.signal)
    await assert.rejects(createPost(body), error => error instanceof PostCreateAuthPendingError)
    assert.equal(calls.length, 1)
  })
  await check('필드 오류 배열/Record·일반 오류·권한/등록 제한 분기 및 실패 원본 보존', async () => {
    const draft = valid(); const before = structuredClone(draft)
    for (const fieldErrors of [[{ field: 'roles[0].roleName', message: '역할명 확인' }, { field: 'event.name', message: '행사명 확인' }], { title: '제목 확인' }]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'INVALID_INPUT', message: '입력 확인', fieldErrors }), { status: 400 })
      await assert.rejects(createPost(model.toCreatePostRequest(draft), authenticated), error => {
        const failure = model.createFailure(error)
        assert.equal(failure.message, '입력 확인')
        assert.deepEqual(failure.fields, Array.isArray(fieldErrors) ? { 'roles[0].roleName': '역할명 확인', 'event.name': '행사명 확인' } : fieldErrors)
        return true
      })
    }
    for (const [code, action] of [['UNAUTHORIZED', 'login'], ['UNVERIFIED', 'verify'], ['SUSPENDED', undefined], ['POST_LIMIT_EXCEEDED', undefined], ['INTERNAL_ERROR', undefined]]) {
      const error = new ApiError(403, { success: false, code, message: '서버 안내' })
      assert.equal(model.createFailure(error).action, action); assert.ok(model.createFailure(error).message)
    }
    assert.deepEqual(draft, before)
  })
  await check('잘못된 성공 응답·HTTP 실패·네트워크 오류를 mock 성공으로 대체하지 않음', async () => {
    for (const data of [null, {}, { id: '42' }, { id: 0 }, { id: -1 }, { id: 1.5 }, { id: Number.MAX_SAFE_INTEGER + 1 }]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: true, data }), { status: 201 })
      await assert.rejects(createPost(model.toCreatePostRequest(valid()), authenticated), error => error.code === 'INVALID_RESPONSE')
    }
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message: '실패' }), { status: 500 })
    await assert.rejects(createPost(model.toCreatePostRequest(valid()), authenticated), error => error.code === 'INTERNAL_ERROR')
    globalThis.fetch = async () => { throw new TypeError('network failed') }
    await assert.rejects(createPost(model.toCreatePostRequest(valid()), authenticated), /network failed/)
    assert.match(model.createFailure(new TypeError('network failed')).message, /등록 결과/)
  })
  function submissionDraft() {
    const date = model.todayInKorea()
    return { ...valid(), deadline: date, periodStart: date, periodEnd: date }
  }
  function observe() {
    const events = []
    return { events, callbacks: {
      onInvalid: errors => events.push(['invalid', errors]), onStart: () => events.push(['start']),
      onSuccess: id => events.push(['navigate', `/posts/${id}`]), onFailure: failure => events.push(['failure', failure]),
      onSettled: () => events.push(['settled']),
    } }
  }
  await check('제출 흐름: 잘못된 입력은 요청하지 않음, 중복 요청 차단, 성공 id 상세 이동', async () => {
    const submitter = createPostSubmitter(); const { events, callbacks } = observe()
    let calls = 0; let resolve
    globalThis.fetch = () => { calls++; return new Promise(done => { resolve = done }) }
    await submitter.submit(model.initialPostDraft(), callbacks)
    assert.equal(calls, 0); assert.equal(events[0][0], 'invalid'); events.length = 0
    const draft = submissionDraft(); const before = structuredClone(draft)
    const pending = submitter.submit(draft, callbacks, authenticated)
    await submitter.submit(draft, callbacks, authenticated)
    assert.equal(calls, 1); assert.deepEqual(events, [['start']])
    resolve(new Response(JSON.stringify({ success: true, data: { id: 77 } }), { status: 201 }))
    await pending
    assert.deepEqual(events, [['start'], ['navigate', '/posts/77'], ['settled']]); assert.deepEqual(draft, before)
  })
  await check('제출 실패: 입력 유지·필드 오류 전달·잠금 해제 후 재시도, 실패 시 이동 없음', async () => {
    const submitter = createPostSubmitter(); const { events, callbacks } = observe()
    const draft = submissionDraft(); const before = structuredClone(draft)
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'INVALID_INPUT', message: '입력 확인', fieldErrors: [{ field: 'roles[0].roleName', message: '역할명 확인' }] }), { status: 400 })
    await submitter.submit(draft, callbacks, authenticated)
    assert.deepEqual(events.map(event => event[0]), ['start', 'failure', 'settled'])
    assert.equal(events[1][1].fields['roles[0].roleName'], '역할명 확인'); assert.deepEqual(draft, before)
    events.length = 0
    globalThis.fetch = async () => new Response(JSON.stringify({ success: true, data: { id: 78 } }), { status: 201 })
    await submitter.submit(draft, callbacks, authenticated)
    assert.deepEqual(events, [['start'], ['navigate', '/posts/78'], ['settled']])
  })
  await check('화면 이탈 시 취소 신호 전달·늦은 결과 및 오류 무시', async () => {
    for (const fails of [false, true]) {
      const submitter = createPostSubmitter(); const { events, callbacks } = observe()
      let complete; let signal
      globalThis.fetch = (_url, options) => { signal = options.signal; return new Promise((resolve, reject) => { complete = () => fails ? reject(new TypeError('late error')) : resolve(new Response(JSON.stringify({ success: true, data: { id: 79 } }), { status: 201 })) }) }
      const pending = submitter.submit(submissionDraft(), callbacks, authenticated)
      submitter.cancel(); assert.equal(signal.aborted, true); complete(); await pending
      assert.deepEqual(events, [['start']])
    }
  })
  await check('토큰 없음·빈 값·공백: API 직접 호출과 유효 폼 반복 제출 모두 fetch 0회·입력 유지', async () => {
    let calls = 0
    globalThis.fetch = async () => { calls++; throw new Error('토큰 없는 요청이 발생하면 실패') }
    const draft = submissionDraft(); const before = structuredClone(draft)
    for (const options of [undefined, {}, { token: '' }, { token: '   ' }, { token: '\t\n' }]) {
      await assert.rejects(createPost(model.toCreatePostRequest(draft), options), error => error instanceof PostCreateAuthPendingError)
      const submitter = createPostSubmitter(); const { events, callbacks } = observe()
      for (let attempt = 0; attempt < 2; attempt++) {
        events.length = 0
        await submitter.submit(draft, callbacks, options)
        assert.deepEqual(events.map(event => event[0]), ['start', 'failure', 'settled'])
        assert.equal(events[1][1].message, postCreateAuthPendingMessage)
        assert.deepEqual(events[1][1].fields, {})
        assert.equal(events[1][1].action, undefined)
        assert.deepEqual(draft, before)
      }
    }
    const { events, callbacks } = observe()
    await createPostSubmitter().submit(model.initialPostDraft(), callbacks)
    assert.equal(events[0][0], 'invalid') // 인증 연동 전에도 입력 검증이 먼저 동작
    assert.equal(events.length, 1)
    assert.equal(calls, 0)
  })
  console.log(`${passed} checks passed (실제 서버·브라우저 검증 아님).`)
} finally { globalThis.fetch = originalFetch; await server.close() }
