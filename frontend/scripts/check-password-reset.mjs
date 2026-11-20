import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
const original = globalThis.fetch
let passed = 0
const check = async (name, fn) => { await fn(); console.log(`PASS ${++passed} ${name}`) }
try {
  const { resetValidation, resetService, createResetController, requestNotice } = await server.ssrLoadModule('/src/features/auth/password-reset.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  await check('email and server password limits / confirmation', () => {
    for (const email of ['', 'a', 'a@', 'a b@c.com']) assert.ok(resetValidation(email))
    assert.equal(resetValidation(' user@example.com '), '')
    for (const value of ['', '12345678', 'abcdefgh', 'abc1234', 'a1' + 'x'.repeat(63)]) assert.ok(resetValidation('', value, value))
    for (const value of ['abcd1234', 'a1' + 'x'.repeat(62)]) assert.equal(resetValidation('', value, value), '')
    assert.ok(resetValidation('', 'abcd1234', 'different'))
  })
  await check('public POST contracts, no bearer/cookies/referrer', async () => {
    let calls = 0
    globalThis.fetch = async (url, options) => {
      assert.equal(options.method, 'POST'); assert.equal(options.headers.get('Authorization'), null)
      assert.equal(options.credentials, 'omit'); assert.equal(options.referrerPolicy, 'no-referrer')
      assert.ok(options.signal)
      assert.equal(url, 'http://localhost:8080/api/auth/password-reset' + (calls ? '/confirm' : ''))
      const body = JSON.parse(options.body)
      assert.deepEqual(Object.keys(body).sort(), calls ? ['newPassword', 'token'] : ['email'])
      calls++; return new Response(JSON.stringify({ success: true, data: null }))
    }
    await resetService.request('test@example.com', new AbortController().signal)
    await resetService.confirm('synthetic-only', 'abcd1234', new AbortController().signal)
    assert.equal(calls, 2)
  })
  await check('invalid input and missing token make zero requests', async () => {
    let calls = 0, state
    const service = { request: async () => { calls++ }, confirm: async () => { calls++ } }
    const c = createResetController(v => { state = v }, service)
    for (const input of [{ email: 'bad' }, { email: '', password: 'abcd1234', confirmation: 'abcd1234' }, { email: '', token: 'synthetic', password: 'weak', confirmation: 'weak' }]) { await c.submit(input); assert.ok(state.error) }
    assert.equal(calls, 0); c.dispose()
  })
  await check('same tick lock, success and duplicate prevention', async () => {
    let calls = 0, resolve, state
    const pending = new Promise(r => { resolve = r })
    const c = createResetController(v => { state = v }, { request: () => { calls++; return pending }, confirm: async () => {} })
    const input = { email: 'test@example.com' }
    const a = c.submit(input), b = c.submit(input); assert.equal(calls, 1); assert.equal(state.pending, true)
    resolve(); await Promise.all([a, b]); assert.equal(state.done, true); await c.submit(input); assert.equal(calls, 1); c.dispose()
    assert.ok(requestNotice.includes('가입된 이메일이라면'))
  })
  await check('network/server/mail failure stays failure, retry succeeds', async () => {
    for (const failure of [new TypeError('offline'), new ApiError(500, { code: 'EMAIL_SEND_FAILED', message: 'failed' })]) {
      let calls = 0, state
      const c = createResetController(v => { state = v }, { request: async () => { if (++calls === 1) throw failure }, confirm: async () => {} })
      await c.submit({ email: 'test@example.com' }); assert.equal(state.done, false); assert.ok(state.error); assert.equal(state.pending, false)
      await c.submit({ email: 'test@example.com' }); assert.equal(state.done, true); c.dispose()
    }
  })
  await check('expired/reused server code disables confirm, no secret echoed', async () => {
    for (const reason of ['expired', 'reused']) {
      let state, calls = 0
      const c = createResetController(v => { state = v }, { request: async () => {}, confirm: async () => { calls++; throw new ApiError(400, { code: 'PASSWORD_RESET_TOKEN_INVALID', message: reason }) } })
      const input = { email: '', token: 'synthetic-only', password: 'abcd1234', confirmation: 'abcd1234' }
      await c.submit(input); assert.equal(state.invalid, true); assert.equal(state.done, false); assert.ok(!JSON.stringify(state).includes(input.token))
      await c.submit(input); assert.equal(calls, 1); c.dispose()
    }
  })
  await check('unmount aborts and ignores late response', async () => {
    let state, resolve, signal
    const c = createResetController(v => { state = v }, { request: (_, s) => { signal = s; return new Promise(r => { resolve = r }) }, confirm: async () => {} })
    const pending = c.submit({ email: 'test@example.com' }); const before = state
    c.dispose(); assert.equal(signal.aborted, true); resolve(); await pending; assert.equal(state, before)
  })
  await check('public static UI, missing token/re-request/login links, token hidden', async () => {
    const { default: Page } = await server.ssrLoadModule('/src/pages/PasswordResetPage.tsx')
    const render = (confirm, path) => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] }, createElement(Page, { confirm })))
    assert.ok(render(false, '/forgot-password').includes('type="email"'))
    for (const confirm of [false, true]) { const logo = render(confirm, confirm ? '/reset-password' : '/forgot-password'); assert.ok(logo.includes('class="teamon-logo"')); assert.ok(logo.includes('brand/team-on.png')); assert.ok(logo.includes('alt="TEAM:ON"')) }
    const missing = render(true, '/reset-password'); assert.ok(!missing.includes('type="password"')); assert.ok(missing.includes('/forgot-password'))
    const html = render(true, '/reset-password?token=synthetic-secret'); assert.ok(html.includes('name="confirmation"')); assert.ok(!html.includes('synthetic-secret')); assert.ok(html.includes('/login'))
  })
  console.log(`${passed} checks passed (intercepted API/controller/static render; no real API/mail/browser).`)
} finally { globalThis.fetch = original; await server.close() }
