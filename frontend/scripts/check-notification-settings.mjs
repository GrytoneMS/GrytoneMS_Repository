// 요청 계약·Controller·공통 인증·정적 렌더 검증. 브라우저 클릭 검증과 구분합니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'; process.env.VITE_POSTS_MOCK = 'false'
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
const original = globalThis.fetch
let passed = 0
const check = async (name, fn) => { await fn(); console.log(`PASS ${++passed} ${name}`) }
const ok = data => new Response(JSON.stringify({ success: true, data }))
const values = { applyEnabled: false, deadlineEnabled: true, quietHoursEnabled: false }
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
try {
  const { notificationSettingsService } = await server.ssrLoadModule('/src/features/settings/api.ts')
  const { createSettingsController, settingsDirty } = await server.ssrLoadModule('/src/features/settings/controller.ts')
  const { createAuthStore } = await server.ssrLoadModule('/src/features/auth/session.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const user = { id: 7, nickname: '설정검증', role: 'USER', status: 'UNVERIFIED' }
  const ready = async () => { const store = createAuthStore(() => 'synthetic-test-only', async () => user); await store.refresh(); return store }
  await check('공통 Bearer/no-store/signal·GET 조회와 정확한 세 boolean PUT·미인증 허용', async () => {
    const store = await ready(), service = notificationSettingsService(store); let requests = 0
    globalThis.fetch = async (url, options) => {
      requests++; assert.equal(url, 'http://localhost:8080/api/users/me/notification-settings')
      assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-test-only'); assert.equal(options.cache, 'no-store'); assert.ok(options.signal)
      if (options.method === 'PUT') assert.deepEqual(JSON.parse(options.body), values)
      else assert.equal(options.body, undefined)
      return ok(values)
    }
    assert.deepEqual(await service.load(new AbortController().signal), values)
    assert.deepEqual(await service.save({ ...values, extra: 'ignored' }, new AbortController().signal), values)
    assert.equal(requests, 2); store.dispose()
  })
  await check('누락/문자열/null 응답을 기본값으로 바꾸지 않음', async () => {
    const store = await ready(), service = notificationSettingsService(store)
    for (const value of [{ applyEnabled: true }, { ...values, quietHoursEnabled: 'false' }, null]) {
      globalThis.fetch = async () => ok(value)
      await assert.rejects(service.load(new AbortController().signal), e => e.code === 'INVALID_RESPONSE')
    }
    store.dispose()
  })
  await check('조회 중/실패 후 저장 차단·재시도 성공만 편집 허용', async () => {
    let state, fail = true, saves = 0
    const service = { current: () => true, load: async () => { if (fail) throw Error('offline'); return values }, save: async () => { saves++; return values } }
    const c = createSettingsController(service, value => { state = value })
    await c.save(); await c.load(); assert.equal(state.phase, 'load-error'); assert.equal(state.draft, null)
    c.change('applyEnabled', true); await c.save(); assert.equal(saves, 0)
    fail = false; await c.load(); assert.deepEqual(state.draft, values); c.change('applyEnabled', true); assert.equal(settingsDirty(state), true); c.dispose()
  })
  await check('저장 실패 입력 유지·동일 tick 중복 저장 방지·성공 응답 반영', async () => {
    let state, saves = 0; const pending = deferred()
    const c = createSettingsController({ current: () => true, load: async () => values, save: async () => { saves++; if (saves === 1) throw Error('offline'); return pending.promise } }, value => { state = value })
    await c.load(); c.change('applyEnabled', true); const draft = { ...state.draft }
    await c.save(); assert.deepEqual(state.draft, draft); assert.deepEqual(state.saved, values); assert.ok(state.error); assert.equal(settingsDirty(state), true)
    const a = c.save(), b = c.save(); assert.equal(saves, 2); assert.equal(state.phase, 'saving')
    c.change('deadlineEnabled', false); assert.equal(state.draft.deadlineEnabled, true)
    pending.resolve(draft); await Promise.all([a, b]); assert.deepEqual(state.saved, draft); assert.equal(settingsDirty(state), false); assert.ok(state.notice); c.dispose()
  })
  await check('이전 GET/저장 응답 폐기·조회 재실행으로 편집값 덮어쓰기 금지', async () => {
    let state, active = true; const late = deferred()
    const c = createSettingsController({ current: () => active, load: () => late.promise, save: async () => values }, value => { state = value })
    const loading = c.load(); active = false; c.dispose(); late.resolve(values); await loading; assert.equal(state.draft, null)
    let loads = 0; const pending = deferred()
    const d = createSettingsController({ current: () => true, load: async () => { loads++; return values }, save: () => pending.promise }, value => { state = value })
    await d.load(); d.change('applyEnabled', true); await d.load(); assert.equal(loads, 1); assert.equal(state.draft.applyEnabled, true)
    const saving = d.save(); d.dispose(); const old = state; pending.resolve(values); await saving; assert.equal(state, old)
  })
  await check('인증 확인 중·비로그인 요청 0·401 만료 처리·403 입력 유지', async () => {
    let token = 'a', calls = 0; const pending = deferred(), store = createAuthStore(() => token, () => pending.promise)
    globalThis.fetch = async () => { calls++; return ok(values) }
    const loading = store.refresh(); await assert.rejects(notificationSettingsService(store).load(new AbortController().signal), e => e.code === 'AUTH_CHECKING'); assert.equal(calls, 0)
    pending.resolve(user); await loading
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'UNAUTHORIZED', message: 'expired' }), { status: 401 })
    await assert.rejects(notificationSettingsService(store).load(new AbortController().signal), e => e.status === 401); assert.equal(store.getSnapshot().status, 'anonymous'); store.dispose()
    let state; const c = createSettingsController({ current: () => true, load: async () => values, save: async () => { throw new ApiError(403, { code: 'FORBIDDEN', message: 'denied' }) } }, value => { state = value })
    await c.load(); c.change('deadlineEnabled', false); await c.save(); assert.equal(state.draft.deadlineEnabled, false); assert.ok(state.error.includes('권한')); c.dispose()
  })
  await check('공통 세션 로그아웃·계정 전환은 요청 취소·이전 서비스 거절·새 값 격리', async () => {
    let token = 'a'; const store = createAuthStore(() => token, async () => ({ ...user, id: token === 'a' ? 7 : 8 })); await store.refresh()
    const old = notificationSettingsService(store), late = deferred(); let signal
    globalThis.fetch = async (_, options) => { signal = options.signal; return late.promise }
    const loading = old.load(new AbortController().signal); token = null; await store.sync(); assert.equal(signal.aborted, true)
    late.resolve(ok(values)); await assert.rejects(loading, e => e.name === 'AbortError')
    token = 'b'; await store.sync(); await assert.rejects(old.save(values, new AbortController().signal), e => e.name === 'AbortError')
    const fresh = { applyEnabled: true, deadlineEnabled: false, quietHoursEnabled: true }; globalThis.fetch = async () => ok(fresh)
    assert.deepEqual(await notificationSettingsService(store).load(new AbortController().signal), fresh); store.dispose()
  })
  await check('비로그인/확인 중 설정 내용 차단·미인증 홈 진입·계정 메뉴 연결', async () => {
    const { AuthContext } = await server.ssrLoadModule('/src/features/auth/context.ts')
    const { default: Home } = await server.ssrLoadModule('/src/pages/SettingsPage.tsx')
    const { default: Settings } = await server.ssrLoadModule('/src/pages/NotificationSettingsPage.tsx')
    const render = (Component, store) => renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(AuthContext.Provider, { value: store }, createElement(Component))))
    const anonymous = createAuthStore(() => null)
    for (const Component of [Home, Settings]) { const html = render(Component, anonymous); assert.ok(html.includes('로그인')); assert.ok(!html.includes('name="applyEnabled"')) }
    const pending = createAuthStore(() => 't', () => new Promise(() => {})); const html = render(Settings, pending); assert.ok(html.includes('확인 중')); assert.ok(!html.includes('변경사항 저장')); pending.dispose()
    const signedIn = await ready(), home = render(Home, signedIn); assert.ok(home.includes('/settings/notifications')); assert.ok(home.includes('차단한 사용자 목록 관리')); assert.ok(home.includes('href="/me/blocks"')); assert.ok(home.includes('회원 탈퇴'))
    assert.ok(home.includes('href="/settings/withdraw"')); assert.ok(home.includes('href="/forgot-password"')); assert.ok(!home.includes('settings-pending')); signedIn.dispose(); anonymous.dispose()
  })
  console.log(`${passed} notification settings checks passed (intercepted API/controller/auth/static render; no browser).`)
} finally { globalThis.fetch = original; await server.close() }
