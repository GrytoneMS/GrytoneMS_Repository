// 요청 계약/상태/정적 렌더 검증. 실제 API 및 브라우저 검증과 구분합니다.
import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import { MemoryRouter } from 'react-router'
process.env.VITE_API_BASE_URL = 'http://localhost:8080'
process.env.VITE_POSTS_MOCK = 'false'
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom' })
const original = globalThis.fetch
globalThis.sessionStorage = { getItem: () => 'test-token' }; globalThis.localStorage = { getItem: () => null }
let passed = 0
const check = async (name, fn) => { await fn(); console.log(`PASS ${++passed} ${name}`) }
const ok = data => new Response(JSON.stringify({ success: true, data }))
const row = id => ({ userId: id, nickname: '검증', schoolName: null, blockedAt: '2026-11-17T12:00:00' })
const page = (items, nextCursor = null) => ({ items, nextCursor, hasNext: nextCursor !== null })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
try {
  const api = await server.ssrLoadModule('/src/features/safety/api.ts')
  const { unblockUser } = await server.ssrLoadModule('/src/features/profile/user-api.ts')
  const { createBlocksController } = await server.ssrLoadModule('/src/features/safety/blocks.ts')
  const { ApiError } = await server.ssrLoadModule('/src/api/client.ts')
  await check('무토큰/잘못된 대상/상세 길이는 요청 전 차단', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; throw Error('unexpected') }
    await assert.rejects(api.createReport('', 'POST', 3, 'ETC', ''))
    await assert.rejects(api.createReport('token', 'POST', 0, 'ETC', ''))
    await assert.rejects(api.createReport('token', 'APPLICATION', 3, 'ETC', 'x'.repeat(201)))
    await assert.rejects(api.getBlocks(' ', null, new AbortController().signal))
    await assert.rejects(unblockUser('', '3')); assert.equal(calls, 0)
  })
  await check('USER/POST/APPLICATION 대상 종류·ID·Bearer·200자·신호 계약', async () => {
    const signal = new AbortController().signal
    for (const type of ['USER', 'POST', 'APPLICATION']) {
      globalThis.fetch = async (url, options) => {
        assert.equal(url, 'http://localhost:8080/api/reports'); assert.equal(options.method, 'POST')
        assert.equal(options.headers.get('Authorization'), 'Bearer token'); assert.equal(options.signal, signal)
        assert.deepEqual(JSON.parse(options.body), { targetType: type, targetId: 37, reasonCode: 'ETC', detail: 'x'.repeat(200) })
        return ok({ reportId: 9, status: 'RECEIVED' })
      }
      await api.createReport('token', type, 37, 'ETC', 'x'.repeat(200), signal)
    }
  })
  await check('중복 409/권한 403/401·응답 오류를 성공으로 바꾸지 않음', async () => {
    for (const [status, code] of [[409, 'ALREADY_REPORTED'], [403, 'UNVERIFIED'], [403, 'FORBIDDEN'], [401, 'UNAUTHORIZED']]) {
      globalThis.fetch = async () => new Response(JSON.stringify({ success: false, code, message: 'denied' }), { status })
      await assert.rejects(api.createReport('t', 'POST', 3, 'ETC', ''), e => e.code === code)
      assert.ok(api.safetyError(new ApiError(status, { code, message: 'denied' })).length > 0)
    }
    globalThis.fetch = async () => ok({ reportId: 9 })
    await assert.rejects(api.createReport('t', 'POST', 3, 'ETC', ''), e => e.code === 'INVALID_RESPONSE')
  })
  await check('차단 커서 원문/첫 페이지 생략·해제 사용자 ID·응답 확인', async () => {
    globalThis.fetch = async url => { assert.ok(url.endsWith('/api/users/me/blocks?size=20')); return ok(page([])) }
    await api.getBlocks('t', null, new AbortController().signal)
    globalThis.fetch = async (url, options) => {
      assert.equal(options.headers.get('Authorization'), 'Bearer t')
      if (options.method === 'DELETE') { assert.ok(url.endsWith('/api/blocks/7')); assert.equal(options.body, undefined); return ok({ userId: 7, isBlocked: false }) }
      assert.ok(url.endsWith('/api/users/me/blocks?size=20&cursor=9223372036854775806')); assert.equal(options.cache, 'no-store')
      return ok(page([row(7)]))
    }
    await api.getBlocks('t', '9223372036854775806', new AbortController().signal); await unblockUser('t', '7')
    globalThis.fetch = async () => ok({ userId: 8, isBlocked: false }); await assert.rejects(unblockUser('t', '7'))
  })
  await check('손상된 커서·빈 다음 페이지·역방향 커서 방어', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; return ok(page([row(7)])) }
    await assert.rejects(api.getBlocks('t', '9223372036854775808', new AbortController().signal)); assert.equal(calls, 0)
    for (const result of [page([], '8'), page([row(7)], '101'), { ...page([]), nextCursor: '9' }]) {
      globalThis.fetch = async () => ok(result)
      await assert.rejects(api.getBlocks('t', '100', new AbortController().signal), e => e.code === 'INVALID_RESPONSE')
    }
  })
  await check('더 보기 실패는 기존 항목 보존·중복 합치기·해제 실패 보존/성공 제거', async () => {
    let state, calls = 0, fail = true
    const c = createBlocksController(v => { state = v }, () => true, {
      load: async cursor => { calls++; if (!cursor) return page([row(7)], '101'); if (fail) throw Error('network'); return page([row(7), row(8)]) },
      remove: async () => { if (fail) throw Error('network') },
    })
    await c.load(); assert.equal(state.cursor, '101'); await c.load(true); assert.equal(state.items.length, 1); assert.ok(state.error)
    await c.remove(7); assert.equal(state.items.length, 1); assert.equal(state.notice, '')
    fail = false; await c.load(true); assert.equal(state.items.length, 2); await c.remove(7); assert.deepEqual(state.items.map(x => x.userId), [8]); assert.ok(state.notice); assert.equal(calls, 3); c.dispose()
  })
  await check('동일 tick 해제 중복 차단·계정 변경/폐기 뒤 늦은 응답 폐기', async () => {
    let state, count = 0, current = true; const pending = deferred()
    const c = createBlocksController(v => { state = v }, () => current, { load: async () => page([row(7)]), remove: async () => { count++; return pending.promise } })
    await c.load(); const a = c.remove(7), b = c.remove(7); assert.equal(count, 1)
    current = false; pending.resolve(); await Promise.all([a, b]); assert.equal(state.items.length, 1); assert.equal(state.notice, ''); c.dispose()
    const late = deferred(); let updates = 0
    const d = createBlocksController(() => updates++, () => true, { load: () => late.promise, remove: async () => {} })
    const loading = d.load(); d.dispose(); const before = updates; late.resolve(page([row(9)])); await loading; assert.equal(updates, before)
  })
  await check('차단 목록 401/403은 이전 개인정보 제거', async () => {
    let state, deny = false
    const c = createBlocksController(v => { state = v }, () => true, { load: async () => { if (deny) throw new ApiError(403, { code: 'FORBIDDEN', message: 'denied' }); return page([row(7)]) }, remove: async () => {} })
    await c.load(); deny = true; await c.load(); assert.deepEqual(state.items, []); assert.equal(state.denied, true); c.dispose()
  })
  await check('신고 UI와 차단 목록 경로 정적 렌더', async () => {
    const { ReportAction } = await server.ssrLoadModule('/src/features/safety/ReportAction.tsx')
    const html = renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(ReportAction, { targetType: 'APPLICATION', targetId: 99, label: '지원 메시지 신고' })))
    assert.ok(html.includes('지원 메시지 신고'))
    const { default: MyBlocksPage } = await server.ssrLoadModule('/src/pages/MyBlocksPage.tsx')
    assert.ok(renderToStaticMarkup(createElement(MemoryRouter, {}, createElement(MyBlocksPage))).includes('내 차단 목록'))
  })
  const { createAuthStore } = await server.ssrLoadModule('/src/features/auth/session.ts')
  const { authenticatedSafety } = await server.ssrLoadModule('/src/features/safety/source.ts')
  const user = { id: 7, nickname: '검증', status: 'VERIFIED', role: 'USER' }
  await check('공통 인증 확인 전 신고/목록/차단/해제는 요청 0회·미인증 신고 차단', async () => {
    let calls = 0; globalThis.fetch = async () => { calls++; return ok(page([])) }
    const pending = deferred(), store = createAuthStore(() => 't', () => pending.promise)
    const loading = store.refresh(), service = authenticatedSafety(store), signal = new AbortController().signal
    for (const request of [() => service.load(null, signal), () => service.remove(9, signal), () => service.block(9, signal), () => service.report('POST', 9, 'ETC', '', signal)]) await assert.rejects(request(), e => e.code === 'AUTH_CHECKING')
    assert.equal(calls, 0); pending.resolve({ ...user, status: 'UNVERIFIED' }); await loading
    const ready = authenticatedSafety(store)
    await assert.rejects(ready.report('POST', 9, 'ETC', '', signal), e => e.code === 'UNVERIFIED'); assert.equal(calls, 0)
    await ready.load(null, signal); assert.equal(calls, 1); store.dispose()
  })
  await check('신고/차단/해제/프로필 공통 Bearer·signal 재사용·추가 사용자 조회 없음', async () => {
    const store = createAuthStore(() => 'synthetic-test-only', async () => user); await store.refresh()
    const service = authenticatedSafety(store), signal = new AbortController().signal, paths = []
    globalThis.fetch = async (url, options) => {
      paths.push(url); assert.equal(options.headers.get('Authorization'), 'Bearer synthetic-test-only'); assert.ok(options.signal)
      if (url.endsWith('/api/reports')) return ok({ reportId: 9, status: 'RECEIVED' })
      if (url.endsWith('/api/blocks/9')) return ok({ userId: 9, isBlocked: false })
      if (url.endsWith('/api/blocks')) return ok({ userId: 9, isBlocked: true })
      return ok(url.includes('/profile') ? { id: 9 } : page([]))
    }
    await service.load(null, signal); await service.block(9, signal); await service.remove(9, signal); await service.profile('9', null, signal); await service.report('APPLICATION', 15, 'ETC', '', signal)
    assert.equal(paths.length, 5); assert.ok(!paths.some(x => x.endsWith('/api/users/me'))); store.dispose()
  })
  await check('로그아웃·계정 전환은 실제 공통 세션의 늦은 목록/해제 응답 폐기·새 목록 격리', async () => {
    let token = 'a', state
    const store = createAuthStore(() => token, async () => ({ ...user, id: token === 'a' ? 7 : 8 })); await store.refresh()
    const service = authenticatedSafety(store), late = deferred(); let oldSignal
    globalThis.fetch = async (_, options) => { oldSignal = options.signal; return late.promise }
    const c = createBlocksController(v => { state = v }, service.current, service), loading = c.load()
    token = null; await store.sync(); assert.equal(oldSignal.aborted, true)
    late.resolve(ok(page([row(99)]))); await loading; assert.deepEqual(state.items, []); c.dispose()
    token = 'a'; await store.sync(); const old = authenticatedSafety(store), pending = deferred()
    globalThis.fetch = async (_, options) => { oldSignal = options.signal; return pending.promise }
    const removing = old.remove(99, new AbortController().signal)
    token = 'b'; await store.sync(); assert.equal(oldSignal.aborted, true)
    pending.resolve(ok({ userId: 99, isBlocked: false })); await assert.rejects(removing, e => e.name === 'AbortError')
    let calls = 0; globalThis.fetch = async () => { calls++; return ok(page([row(10)])) }
    await assert.rejects(old.load(null, new AbortController().signal), e => e.name === 'AbortError'); assert.equal(calls, 0)
    const fresh = authenticatedSafety(store); assert.deepEqual((await fresh.load(null, new AbortController().signal)).items.map(x => x.userId), [10]); assert.equal(calls, 1); store.dispose()
  })
  console.log(`${passed} safety checks passed (intercepted API/controller/static render; no browser).`)
} finally { globalThis.fetch = original; delete globalThis.sessionStorage; delete globalThis.localStorage; await server.close() }
