import { ApiError, apiRequest } from '../../api/client'

export type Account = { id: number; nickname: string | null; status: 'UNVERIFIED' | 'VERIFIED' | 'SUSPENDED' | 'WITHDRAWN'; role: 'USER' | 'ADMIN' }
export type Session = { userId: string; token: string; account: Account }
export type AuthState = { status: 'checking' | 'anonymous' | 'authenticated' | 'error'; session?: Session; message: string; epoch: number }
export async function loadAccount(token: string, signal: AbortSignal): Promise<Account> {
  const value = await apiRequest<Account>('/api/users/me', { token, signal, cache: 'no-store' })
  if (!value || !Number.isSafeInteger(value.id) || value.id <= 0 || !(value.nickname === null || typeof value.nickname === 'string') || !['UNVERIFIED', 'VERIFIED', 'SUSPENDED', 'WITHDRAWN'].includes(value.status) || !['USER', 'ADMIN'].includes(value.role)) throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '로그인 사용자 정보를 확인하지 못했어요.' })
  return value
}
export function createAuthStore(read: () => string | null, load = loadAccount) {
  let token = read()?.trim() || null, state: AuthState = { status: token ? 'checking' : 'anonymous', message: '', epoch: 0 }
  let active = true, sequence = 0, controller = new AbortController()
  let accountId: number | undefined
  const listeners = new Set<() => void>()
  const publish = (patch: Partial<AuthState>) => { state = { ...state, ...patch }; listeners.forEach(fn => fn()) }
  const current = () => active && (read()?.trim() || null) === token
  async function refresh() {
    if (!active) return
    const next = read()?.trim() || null, changed = next !== token
    if (changed) accountId = undefined
    token = next; controller.abort(); controller = new AbortController(); const signal = controller.signal, version = ++sequence
    publish({ status: token ? 'checking' : 'anonymous', session: undefined, message: '', epoch: state.epoch + (changed ? 1 : 0) })
    if (!token) return
    const requested = token
    try {
      const account = await load(requested, signal)
      if (!current() || signal.aborted || version !== sequence) return
      if (account.status === 'WITHDRAWN') { publish({ status: 'anonymous', message: '사용할 수 없는 계정입니다.' }); return }
      const identityChanged = accountId !== undefined && accountId !== account.id
      accountId = account.id
      publish({ status: 'authenticated', session: { token: requested, userId: String(account.id), account }, epoch: state.epoch + (identityChanged ? 1 : 0) })
    } catch (error) {
      if (!current() || signal.aborted || version !== sequence) return
      publish({ status: error instanceof ApiError && error.status === 401 ? 'anonymous' : 'error', session: undefined,
        message: error instanceof ApiError && error.status === 401 ? '로그인이 만료됐어요. 다시 로그인해 주세요.' : error instanceof ApiError && error.status === 403 ? '사용자 정보를 조회할 권한이 없어요.' : '로그인 상태 조회에 실패했어요. 연결을 확인하고 다시 조회해 주세요.' })
    }
  }
  function requireSession(verified = false) {
    if (!current() || state.status === 'checking') throw new ApiError(401, { success: false, code: 'AUTH_CHECKING', message: '로그인 상태를 확인 중이에요. 입력은 유지됩니다.' })
    if (state.status === 'error') throw new ApiError(503, { success: false, code: 'AUTH_LOOKUP_FAILED', message: state.message })
    if (state.status !== 'authenticated' || !state.session) throw new ApiError(401, { success: false, code: 'UNAUTHORIZED', message: '로그인이 필요해요. 입력은 유지됩니다.' })
    if (verified && state.session.account.status !== 'VERIFIED') throw new ApiError(403, { success: false, code: state.session.account.status === 'SUSPENDED' ? 'SUSPENDED' : 'UNVERIFIED', message: '학교 인증 상태를 확인해 주세요.' })
    return state.session
  }
  async function execute<T>(operation: (session: Session, signal: AbortSignal) => Promise<T>, verified = false): Promise<T> {
    const session = requireSession(verified), version = sequence, signal = controller.signal
    try {
      const result = await operation(session, signal)
      if (!current() || signal.aborted || version !== sequence) throw new DOMException('계정이 변경됐습니다.', 'AbortError')
      return result
    } catch (error) {
      if (!current() || signal.aborted || version !== sequence) throw new DOMException('계정이 변경됐습니다.', 'AbortError')
      if (error instanceof ApiError && error.status === 401) { sequence++; controller.abort(); controller = new AbortController(); publish({ status: 'anonymous', session: undefined, message: '로그인이 만료됐어요. 입력은 유지됩니다. 다시 로그인해 주세요.' }) }
      throw error
    }
  }
  return { getSnapshot: () => state, subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } }, refresh,
    sync: () => { if ((read()?.trim() || null) !== token) return refresh() }, requireSession, execute,
    resume() { active = true }, dispose() { active = false; sequence++; controller.abort() } }
}
export type AuthStore = ReturnType<typeof createAuthStore>
