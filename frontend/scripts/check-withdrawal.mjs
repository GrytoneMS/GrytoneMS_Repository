import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
const original = globalThis.fetch
const counts = { openPostCount: 2, acceptedTeamCount: 3 }
const ok = data => new Response(JSON.stringify({ success: true, data }))
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
let passed = 0
const check = async (name, fn) => { await fn(); console.log(`PASS ${++passed} ${name}`) }
try {
  const { withdrawalService } = await server.ssrLoadModule('/src/features/withdrawal/api.ts')
  const { createWithdrawalController } = await server.ssrLoadModule('/src/features/withdrawal/controller.ts')
  const { createAuthStore } = await server.ssrLoadModule('/src/features/auth/session.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  const user = { id: 7, nickname: 'synthetic', role: 'USER', status: 'UNVERIFIED' }
  const ready = async () => { const store = createAuthStore(() => 'synthetic-only', async () => user); await store.refresh(); return store }
  const fake = overrides => ({ current: () => true, load: async () => counts, remove: async () => null, checkSession: async () => user, ...overrides })
  await check('authenticated GET/DELETE password body and no school gate', async () => {
    const store = await ready(), service = withdrawalService(store); let calls = 0
    globalThis.fetch = async (url, options) => {
      assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-only'); assert.equal(options.cache, 'no-store'); assert.ok(options.signal)
      if (calls++ === 0) { assert.ok(url.endsWith('/withdrawal-check')); assert.equal(options.body, undefined); return ok(counts) }
      assert.ok(url.endsWith('/api/users/me')); assert.equal(options.method, 'DELETE'); assert.deepEqual(Object.keys(JSON.parse(options.body)), ['password']); return ok(null)
    }
    assert.deepEqual(await service.load(new AbortController().signal), counts)
    await service.remove('synthetic-password', new AbortController().signal); assert.equal(calls, 2); store.dispose()
  })
  await check('bad counts fail instead of zero fallback', async () => {
    const store = await ready(), service = withdrawalService(store)
    for (const data of [null, {}, { ...counts, openPostCount: -1 }, { ...counts, acceptedTeamCount: '0' }]) {
      globalThis.fetch = async () => ok(data)
      await assert.rejects(service.load(new AbortController().signal), e => e.code === 'INVALID_RESPONSE')
    }
    store.dispose()
  })
  await check('precheck failure blocks deletion and retry loads actual counts', async () => {
    let state, fail = true, deletes = 0
    const c = createWithdrawalController(fake({ load: async () => { if (fail) throw Error(); return counts }, remove: async () => { deletes++ } }), s => { state = s }, () => {})
    await c.load(); assert.equal(state.phase, 'load-error'); assert.equal(state.counts, null)
    await c.submit('synthetic', true); assert.equal(deletes, 0)
    fail = false; await c.load(); assert.deepEqual(state.counts, counts); c.dispose()
  })
  await check('confirmation cancel/empty input make no DELETE, counts do not restrict', async () => {
    let state, deletes = 0
    const c = createWithdrawalController(fake({ remove: async () => { deletes++ } }), s => { state = s }, () => {})
    await c.load(); await c.submit('synthetic', false); await c.submit('', true); assert.equal(deletes, 0); assert.equal(state.phase, 'ready')
    await c.submit('synthetic', true); assert.equal(deletes, 1); c.dispose()
  })
  await check('wrong password stays retryable; same tick duplicate blocked', async () => {
    let state, deletes = 0, completed = 0; const pending = deferred()
    const c = createWithdrawalController(fake({ remove: async () => { if (++deletes === 1) throw new ApiError(400, { code: 'PASSWORD_MISMATCH', message: 'bad' }); return pending.promise } }), s => { state = s }, () => { completed++ })
    await c.load(); await c.submit('synthetic', true); assert.equal(state.phase, 'ready'); assert.ok(state.error.includes('비밀번호'))
    const a = c.submit('synthetic', true), b = c.submit('synthetic', true); assert.equal(deletes, 2); assert.equal(state.phase, 'sending')
    pending.resolve(null); await Promise.all([a, b]); assert.equal(completed, 1); assert.equal(state.counts, null); c.dispose()
  })
  await check('lost response/5xx are uncertain; status check never replays DELETE', async () => {
    for (const error of [new TypeError('lost'), new ApiError(500, { code: 'INTERNAL_ERROR', message: 'failed' }), new ApiError(200, { code: 'INVALID_RESPONSE', message: 'invalid' })]) {
      let state, deletes = 0, checks = 0, completed = 0
      const c = createWithdrawalController(fake({ remove: async () => { deletes++; throw error }, checkSession: async () => { checks++ } }), s => { state = s }, () => { completed++ })
      await c.load(); await c.submit('synthetic', true); assert.equal(state.phase, 'uncertain')
      await c.submit('synthetic', true); await c.checkSession(); assert.equal(deletes, 1); assert.equal(checks, 1); assert.equal(completed, 0); assert.equal(state.phase, 'uncertain'); c.dispose()
    }
  })
  await check('success uses common logout and removes session/private scope', async () => {
    const { clearAccessToken, saveAccessToken, readAccessToken } = await server.ssrLoadModule('/src/features/auth/api.ts')
    const saved = { window: globalThis.window, localStorage: globalThis.localStorage, sessionStorage: globalThis.sessionStorage }
    const storage = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key) } }
    globalThis.window = new EventTarget(); globalThis.localStorage = storage(); globalThis.sessionStorage = storage()
    let store
    try {
      saveAccessToken('synthetic-only', true); store = createAuthStore(readAccessToken, async () => user); await store.refresh(); const epoch = store.getSnapshot().epoch
      let completed = 0
      const c = createWithdrawalController(fake(), () => {}, () => { completed++; clearAccessToken(); void store.sync() })
      await c.load(); await c.submit('synthetic', true)
      assert.equal(completed, 1); assert.equal(readAccessToken(), null); assert.equal(store.getSnapshot().session, undefined); assert.equal(store.getSnapshot().status, 'anonymous'); assert.ok(store.getSnapshot().epoch > epoch); c.dispose()
    } finally { store?.dispose(); Object.assign(globalThis, saved) }
  })
  await check('401 clears private session, 403 remains a server rejection', async () => {
    const store = await ready(), service = withdrawalService(store)
    globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code: 'UNAUTHORIZED', message: 'expired' }), { status: 401 })
    await assert.rejects(service.load(new AbortController().signal), e => e.status === 401); assert.equal(store.getSnapshot().session, undefined); store.dispose()
    let state
    const c = createWithdrawalController(fake({ remove: async () => { throw new ApiError(403, { code: 'FORBIDDEN', message: 'forbidden' }) } }), s => { state = s }, () => {})
    await c.load(); await c.submit('synthetic', true); assert.equal(state.phase, 'ready'); assert.ok(state.error.includes('권한')); c.dispose()
  })
  await check('account switch aborts old response and never logs out new account', async () => {
    let token = 'a'; const store = createAuthStore(() => token, async () => ({ ...user, id: token === 'a' ? 7 : 8 })); await store.refresh()
    const service = withdrawalService(store), pending = deferred(); let completed = 0
    globalThis.fetch = async url => url.endsWith('/withdrawal-check') ? ok(counts) : pending.promise
    const c = createWithdrawalController(service, () => {}, () => { completed++ }); await c.load(); const deleting = c.submit('synthetic', true)
    token = 'b'; await store.sync(); pending.resolve(ok(null)); await deleting
    assert.equal(completed, 0); assert.equal(store.getSnapshot().session.userId, '8'); c.dispose(); store.dispose()
  })
  await check('anonymous/checking UI hides password and completion has home/login links', async () => {
    const { AuthContext } = await server.ssrLoadModule('/src/features/auth/context.ts')
    const { default: Page } = await server.ssrLoadModule('/src/pages/WithdrawPage.tsx')
    const render = (store, state) => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [{ pathname: '/settings/withdraw', state }] }, createElement(AuthContext.Provider, { value: store }, createElement(Page))))
    for (const store of [createAuthStore(() => null), createAuthStore(() => 'synthetic', () => new Promise(() => {}))]) {
      assert.ok(!render(store).includes('type="password"')); store.dispose()
    }
    const store = createAuthStore(() => null), html = render(store, { withdrawalCompleted: true })
    assert.ok(html.includes('class="teamon-logo"')); assert.ok(html.includes('brand/team-on.png')); assert.ok(html.includes('alt="TEAM:ON"'))
    assert.ok(html.includes('회원 탈퇴가 완료')); assert.ok(html.includes('href="/posts"')); assert.ok(html.includes('href="/login"')); store.dispose()
  })
  console.log(`${passed} withdrawal checks passed (intercepted API/controller/auth/static render; no real deletion/browser).`)
} finally { globalThis.fetch = original; await server.close() }
