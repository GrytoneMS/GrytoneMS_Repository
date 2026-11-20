import assert from 'node:assert/strict'
import { createServer } from 'vite'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'true'
const oldFetch = globalThis.fetch
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
let passed = 0
async function check(name, run) { await run(); console.log(`PASS ${++passed} ${name}`) }
try {
  const m = await server.ssrLoadModule('/src/features/posts/edit-model.ts')
  const { createEditSubmitter } = await server.ssrLoadModule('/src/features/posts/edit-submit.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const ex = await server.ssrLoadModule('/src/features/posts/edit-examples.ts')
  const s = await ex.loadEditExample('1', 'success'), valid = () => m.editDraft(structuredClone(s)), token = { token: 'test-only' }
  const json = data => new Response(JSON.stringify({ success: true, data }), { headers: { 'Content-Type': 'application/json' } })
  await check('초기값 원문·연락처·기존 역할 ID·null/빈/공백 요구조건 보존', () => {
    const before = JSON.stringify(s), draft = valid(), body = m.toEditRequest(draft, s)
    assert.deepEqual(m.validateEditDraft(draft, s), {}); assert.equal(body.contactLink, s.contactLink)
    assert.deepEqual(body.roles.map(r => r.id), s.post.roles.map(r => r.id)); assert.equal(JSON.stringify(s), before)
    for (const requirements of [null, '', '  ']) {
      const snap = structuredClone(s); snap.post.roles[0].requirements = requirements; snap.contactLink = 'https://EXAMPLE.com:443'
      const req = m.toEditRequest(m.editDraft(snap), snap)
      assert.equal(req.roles[0].requirements, requirements); assert.equal(req.contactLink, snap.contactLink)
    }
    assert.ok(!('key' in body.roles[0])); assert.ok(!('filledCount' in body.roles[0])); assert.ok(!('status' in body))
  })
  await check('지원 역할 분류/인원 감소 제한·이름/조건/증원 허용·기존 삭제 금지', () => {
    const d = valid(), rules = m.roleRules(s)
    assert.equal(rules[31].codeLocked, false); assert.equal(rules[32].codeLocked, true); assert.ok(rules[31].deletionReason)
    d.roles[1].roleCode = 'DEV'; d.roles[1].headcount = '1'
    const errors = m.validateEditDraft(d, s); assert.ok(errors['roles[1].roleCode']); assert.ok(errors['roles[1].headcount'])
    d.roles[1].roleCode = 'DESIGN'; d.roles[1].headcount = '3'; d.roles[1].roleName = '변경한 역할 이름'; d.roles[1].requirements = '새 조건'
    assert.deepEqual(m.validateEditDraft(d, s), {}); assert.equal(m.toEditRequest(d, s).roles[1].id, 32)
    d.roles.shift(); assert.ok(m.validateEditDraft(d, s).roles)
  })
  await check('새 역할 id=null·중복/미지 ID 차단·지난 마감일 유지 불가', async () => {
    const d = valid(); d.roles.push({ ...d.roles[0], key: -1 })
    assert.deepEqual(m.validateEditDraft(d, s), {}); assert.equal(m.toEditRequest(d, s).roles[2].id, null)
    d.roles[2].key = 31; assert.ok(m.validateEditDraft(d, s).roles)
    d.roles[2].key = 999; assert.ok(m.validateEditDraft(d, s).roles)
    const closed = await ex.loadEditExample('1', 'closed'), cd = m.editDraft(closed)
    assert.ok(m.validateEditDraft(cd, closed).deadline); cd.deadline = s.post.deadline
    assert.deepEqual(m.validateEditDraft(cd, closed), {})
  })
  await check('토큰 없음/공백: 초기 조회와 수정 fetch 0회·입력 불변', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('unexpected') }
    const d = valid(), before = JSON.stringify(d)
    for (const value of [undefined, '', '  ']) {
      await assert.rejects(m.loadEditSnapshot('1', { token: value }), e => e.code === 'AUTH_PENDING')
      await assert.rejects(m.updatePost(1, m.toEditRequest(d, s), { token: value }), e => e.code === 'AUTH_PENDING')
    }
    assert.equal(calls, 0); assert.equal(JSON.stringify(d), before)
  })
  await check('본인 확인 뒤 별도 연락처+지원자 조회·누락/권한/현황 불일치 차단', async () => {
    const urls = []
    globalThis.fetch = async (url, init) => { urls.push(url); assert.equal(init.headers.get('Authorization'), 'Bearer test-only'); return json(url.endsWith('/contact') ? { contactLink: s.contactLink } : url.endsWith('/applications') ? s.applicants : s.post) }
    assert.deepEqual(await m.loadEditSnapshot('1', token), s); assert.equal(urls.length, 3)
    globalThis.fetch = async url => json(url.endsWith('/contact') ? {} : url.endsWith('/applications') ? s.applicants : s.post)
    await assert.rejects(m.loadEditSnapshot('1', token), e => e.code === 'INVALID_RESPONSE')
    let calls = 0; globalThis.fetch = async () => { calls++; return json({ ...s.post, isMine: false }) }
    await assert.rejects(m.loadEditSnapshot('1', token), e => e.code === 'FORBIDDEN'); assert.equal(calls, 1)
    const broken = structuredClone(s); broken.applicants[0].headcount++; assert.throws(() => m.checkSnapshot(broken, '1'))
    for (const key of ['locationText', 'meetingFreq']) { const broken = structuredClone(s); delete broken.post[key]; assert.throws(() => m.checkSnapshot(broken, '1')) }
  })
  await check('PUT 경로·전체 본문·Bearer/signal·HTTP/네트워크 실패를 mock 성공으로 바꾸지 않음', async () => {
    const body = m.toEditRequest(valid(), s), signal = new AbortController().signal
    globalThis.fetch = async (url, init) => { assert.equal(url, 'http://localhost:8080/api/posts/1'); assert.equal(init.method, 'PUT'); assert.equal(init.signal, signal); assert.deepEqual(JSON.parse(init.body), body); return json(s.post) }
    await m.updatePost(1, body, { ...token, signal })
    for (const [status, code] of [[400, 'INVALID_INPUT'], [401, 'UNAUTHORIZED'], [403, 'FORBIDDEN'], [404, 'POST_DELETED'], [409, 'ROLE_HAS_APPLICANTS'], [500, 'INTERNAL_ERROR']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: code }), { status })
      await assert.rejects(m.updatePost(1, body, token), e => e.code === code)
    }
    globalThis.fetch = async () => { throw Error('network') }; await assert.rejects(m.updatePost(1, body, token))
  })
  function observer() { const events = []; return { events, cb: { invalid: e => events.push(['invalid', e]), start: () => events.push(['start']), success: () => events.push(['navigate']), failure: e => events.push(['failure', e]), settled: () => events.push(['settled']) } } }
  await check('같은 tick 중복 제출 1회·성공 이동 콜백·입력 불변', async () => {
    let finish, calls = 0; const c = createEditSubmitter(s, () => { calls++; return new Promise(r => { finish = r }) }), o = observer(), d = valid(), before = JSON.stringify(d)
    const first = c.submit(d, o.cb); await c.submit(d, o.cb); assert.equal(calls, 1); finish(); await first
    assert.deepEqual(o.events.map(e => e[0]), ['start', 'navigate', 'settled']); assert.equal(JSON.stringify(d), before)
  })
  await check('검증 오류 무요청·실패 입력 유지·필드 오류·잠금 해제 후 재시도', async () => {
    let calls = 0; const c = createEditSubmitter(s, async () => { calls++; throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: 'bad', fieldErrors: [{ field: 'title', message: '제목 오류' }] }) }), o = observer(), d = valid(), before = JSON.stringify(d)
    await c.submit({ ...d, title: '' }, o.cb); assert.equal(calls, 0)
    await c.submit(d, o.cb); await c.submit(d, o.cb); assert.equal(calls, 2); assert.equal(JSON.stringify(d), before)
    assert.equal(o.events.filter(e => e[0] === 'navigate').length, 0); assert.equal(o.events.find(e => e[0] === 'failure')[1].fields.title, '제목 오류')
  })
  await check('페이지 이동 시 abort·뒤늦은 성공/실패 무시', async () => {
    for (const fail of [false, true]) {
      let finish, signal; const c = createEditSubmitter(s, (_d, value) => { signal = value; return new Promise((r, j) => { finish = () => fail ? j(Error('late')) : r() }) }), o = observer()
      const work = c.submit(valid(), o.cb); c.cancel(); assert.equal(signal.aborted, true); finish(); await work; assert.deepEqual(o.events.map(e => e[0]), ['start'])
    }
  })
  await check('예시 저장 후 최신 상세·서버 오류/제한·초기 실패·실제 fetch 0회', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('unexpected') }
    const d = valid(); d.title = '수정 완료된 예시 모집글'; d.contactLink = 'https://open.kakao.com/o/updated'; await ex.saveEditExample(d, s, 'success')
    const { mockDetail } = await server.ssrLoadModule('/src/features/posts/mock.ts'); assert.equal(mockDetail('1', 'owner').title, d.title)
    assert.ok(!('contactLink' in mockDetail('1', 'guest')))
    assert.equal((await ex.loadEditExample('1', 'success')).contactLink, d.contactLink)
    for (const outcome of ['invalid', 'restricted', 'failure', 'forbidden']) await assert.rejects(ex.saveEditExample(d, s, outcome))
    for (const outcome of ['loadFailure', 'contactFailure', 'forbidden', 'missing', 'deleted']) await assert.rejects(ex.loadEditExample('1', outcome))
    assert.equal(calls, 0); assert.match(m.editFailure(Error('network'), 'load').message, /불러오지/)
  })
  await check('공통 폼 정적 렌더: 수정 제목/제출·기존 값·잠금 이유·필드 오류·작성 유지', async () => {
    const React = await import('react'), { renderToStaticMarkup } = await import('react-dom/server'), { MemoryRouter } = await import('react-router')
    const { default: Form } = await server.ssrLoadModule('/src/pages/PostCreatePage.tsx')
    const html = renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(Form, { editor: { initial: valid(), rules: m.roleRules(s), notice: null, submit: async () => {}, cancel() {}, errors: { title: '서버 필드 오류' }, failure: '수정 거절', submitting: false, onChange() {} } })))
    for (const text of ['모집글 수정', '수정 완료', s.post.title, '수정 거절', '서버 필드 오류', '취소된 지원 이력']) assert.ok(html.includes(text))
    const create = renderToStaticMarkup(React.createElement(MemoryRouter, null, React.createElement(Form))); assert.ok(create.includes('모집글 작성')); assert.ok(create.includes('모집글 등록'))
  })
  console.log(`${passed} edit checks passed (intercepted API/state/static render; no real server/DB/browser).`)
} finally { globalThis.fetch = oldFetch; await server.close() }
