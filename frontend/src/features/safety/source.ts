import type { AuthStore } from '../auth/session'
import { blockUser, getUserProfile, unblockUser } from '../profile/user-api'
import { createReport, getBlocks, type ReportReason, type ReportTarget } from './api'

/** 모든 개인 요청을 확인된 동일 세션과 공통 취소 신호에 묶습니다. */
export function authenticatedSafety(store: AuthStore, session = store.getSnapshot().session) {
  const current = () => !!session && store.getSnapshot().status === 'authenticated' && store.getSnapshot().session === session
  function run<T>(signal: AbortSignal, operation: (token: string, signal: AbortSignal) => Promise<T>, verified = false) {
    return store.execute((active, authSignal) => {
      if (active !== session) throw new DOMException('계정이 변경됐습니다.', 'AbortError')
      return operation(active.token, AbortSignal.any([signal, authSignal]))
    }, verified)
  }
  return {
    current,
    report: (type: ReportTarget, id: number, reason: ReportReason, detail: string, signal: AbortSignal) => run(signal, (token, combined) => createReport(token, type, id, reason, detail, combined), true),
    load: (cursor: string | null, signal: AbortSignal) => run(signal, (token, combined) => getBlocks(token, cursor, combined)),
    remove: (id: number, signal: AbortSignal) => run(signal, (token, combined) => unblockUser(token, String(id), combined)),
    block: (id: number, signal: AbortSignal) => run(signal, (token, combined) => blockUser(token, id, combined)),
    profile: (id: string, applicationId: string | null, signal: AbortSignal) => run(signal, (token, combined) => getUserProfile(token, id, applicationId, combined)),
  }
}
export type SafetyService = ReturnType<typeof authenticatedSafety>
