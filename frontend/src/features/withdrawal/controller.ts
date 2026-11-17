import { ApiError } from '../../api/client'
import type { WithdrawalCheck, WithdrawalService } from './api'

export type WithdrawalState = { phase: 'loading' | 'load-error' | 'ready' | 'sending' | 'uncertain' | 'checking' | 'done'; counts: WithdrawalCheck | null; error: string }
export const initialWithdrawal: WithdrawalState = { phase: 'loading', counts: null, error: '' }
export function createWithdrawalController(service: WithdrawalService, publish: (state: WithdrawalState) => void, complete: () => void) {
  let state = { ...initialWithdrawal }, active = true, busy = false
  const request = new AbortController()
  const current = () => active && service.current() && !request.signal.aborted
  const update = (patch: Partial<WithdrawalState>) => { state = { ...state, ...patch }; publish(state) }
  async function load() {
    if (!current() || busy || !['loading', 'load-error'].includes(state.phase)) return
    busy = true; update({ phase: 'loading', error: '' })
    try { const counts = await service.load(request.signal); if (current()) update({ phase: 'ready', counts }) }
    catch { if (current()) update({ phase: 'load-error', counts: null, error: '탈퇴 전 정보를 불러오지 못했어요. 다시 조회해 주세요.' }) }
    finally { busy = false }
  }
  async function submit(password: string, confirmed: boolean) {
    if (!current() || busy || state.phase !== 'ready' || !state.counts) return
    if (!confirmed || !password.trim()) { update({ error: !confirmed ? '탈퇴 내용을 확인하고 최종 확인을 선택해 주세요.' : '현재 비밀번호를 입력해 주세요.' }); return }
    busy = true; update({ phase: 'sending', error: '' })
    try {
      await service.remove(password, request.signal)
      if (current()) { update({ phase: 'done', counts: null }); complete() }
    } catch (error) {
      if (!current()) return
      if (error instanceof ApiError && error.code === 'PASSWORD_MISMATCH') update({ phase: 'ready', error: '비밀번호가 맞지 않아요. 다시 입력해 주세요.' })
      else if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.code !== 'INVALID_RESPONSE') update({ phase: 'ready', error: error.status === 403 ? '탈퇴 요청 권한을 확인해 주세요.' : '요청이 거절됐어요. 입력과 로그인 상태를 확인해 주세요.' })
      else update({ phase: 'uncertain', error: '탈퇴 결과를 확인할 수 없어요. 이미 처리됐을 수 있으므로 다시 전송하지 않습니다. 로그인 상태를 조회하거나 다시 로그인해 계정 상태를 확인해 주세요. 로그인 불가는 탈퇴 완료의 증거가 아닙니다.' })
    } finally { busy = false }
  }
  async function checkSession() {
    if (!current() || busy || state.phase !== 'uncertain') return
    busy = true; update({ phase: 'checking' })
    try { await service.checkSession(request.signal); if (current()) update({ phase: 'uncertain', error: '현재 계정 조회는 가능하지만 앞선 요청의 최종 결과는 확정할 수 없어요. 잠시 후 다시 로그인해 확인해 주세요. 탈퇴 요청은 재전송하지 않았습니다.' }) }
    catch { if (current()) update({ phase: 'uncertain', error: '계정 상태도 확인하지 못했어요. 연결을 확인하거나 다시 로그인해 주세요. 탈퇴 완료로 단정할 수 없습니다.' }) }
    finally { busy = false }
  }
  return { load, submit, checkSession, dispose() { active = false; request.abort() } }
}
