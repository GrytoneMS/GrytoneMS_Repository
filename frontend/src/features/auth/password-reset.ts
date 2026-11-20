import { ApiError, apiRequest } from '../../api/client'

export const requestNotice = '가입된 이메일이라면 비밀번호 재설정 안내가 발송됩니다. 메일함과 스팸함을 확인해 주세요.'
export function resetValidation(email: string, password?: string, confirmation?: string) {
  if (password === undefined) return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) ? '' : '올바른 이메일을 입력해 주세요.'
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,64}$/.test(password)) return '비밀번호는 영문과 숫자를 포함해 8~64자로 입력해 주세요.'
  return password === confirmation ? '' : '비밀번호 확인값이 일치하지 않아요.'
}
export const resetService = {
  request: (email: string, signal: AbortSignal) => apiRequest<null>('/api/auth/password-reset', { method: 'POST', body: { email: email.trim() }, signal, credentials: 'omit', referrerPolicy: 'no-referrer' }),
  confirm: (token: string, newPassword: string, signal: AbortSignal) => apiRequest<null>('/api/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword }, signal, credentials: 'omit', referrerPolicy: 'no-referrer' }),
}
export type ResetState = { pending: boolean; done: boolean; invalid: boolean; error: string }
export const initialReset: ResetState = { pending: false, done: false, invalid: false, error: '' }
// Per-form lock also prevents two submissions within the same React render.
export function createResetController(publish: (state: ResetState) => void, service = resetService) {
  let state = { ...initialReset }, active = true
  const abort = new AbortController()
  async function submit(input: { email: string; token?: string; password?: string; confirmation?: string }) {
    if (!active || state.pending || state.done || state.invalid) return
    const error = input.password !== undefined && !input.token ? '링크가 올바르지 않아요. 재설정 메일을 다시 요청해 주세요.' : resetValidation(input.email, input.password, input.confirmation)
    if (error) { state = { ...state, error }; publish(state); return }
    state = { ...state, pending: true, error: '' }; publish(state)
    try {
      if (input.password !== undefined) await service.confirm(input.token!, input.password, abort.signal)
      else await service.request(input.email, abort.signal)
      if (active) { state = { ...state, pending: false, done: true }; publish(state) }
    } catch (error) {
      if (!active) return
      const invalid = error instanceof ApiError && error.code === 'PASSWORD_RESET_TOKEN_INVALID'
      state = { ...state, pending: false, invalid, error: invalid ? '링크가 만료됐거나 이미 사용됐어요. 재설정 메일을 다시 요청해 주세요.' : '요청 결과를 확인하지 못했어요. 연결을 확인하고 다시 시도해 주세요. 계속 실패하면 메일을 다시 요청해 주세요.' }; publish(state)
    }
  }
  return { submit, dispose() { active = false; abort.abort() } }
}
