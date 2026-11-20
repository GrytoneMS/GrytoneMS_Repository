import { ApiError, apiRequest } from '../../api/client'
import type { AuthStore } from '../auth/session'

export type WithdrawalCheck = { openPostCount: number; acceptedTeamCount: number }
export function withdrawalService(store: AuthStore, session = store.getSnapshot().session) {
  const current = () => !!session && store.getSnapshot().status === 'authenticated' && store.getSnapshot().session === session
  const request = <T,>(path: string, signal: AbortSignal, password?: string) => store.execute((active, authSignal) => {
    if (active !== session) throw new DOMException('계정이 변경됐습니다.', 'AbortError')
    return apiRequest<T>(path, { token: active.token, signal: AbortSignal.any([signal, authSignal]), cache: 'no-store', ...(password !== undefined ? { method: 'DELETE', body: { password } } : {}) })
  })
  return {
    current,
    async load(signal: AbortSignal) {
      const value = await request<WithdrawalCheck>('/api/users/me/withdrawal-check', signal)
      if (!value || ![value.openPostCount, value.acceptedTeamCount].every(n => Number.isSafeInteger(n) && n >= 0)) throw new ApiError(200, { code: 'INVALID_RESPONSE', success: false, message: '탈퇴 전 확인 응답을 확인하지 못했어요.' })
      return value
    },
    remove: (password: string, signal: AbortSignal) => request<null>('/api/users/me', signal, password),
    checkSession: (signal: AbortSignal) => request<unknown>('/api/users/me', signal),
  }
}
export type WithdrawalService = ReturnType<typeof withdrawalService>
