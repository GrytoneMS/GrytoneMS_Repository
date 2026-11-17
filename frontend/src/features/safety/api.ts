import { ApiError, apiRequest } from '../../api/client'
import { reportReasons, reportTarget } from '../profile/user-api'

export type ReportTarget = 'USER' | 'POST' | 'APPLICATION'
export type ReportReason = keyof typeof reportReasons
export type BlockedUser = { userId: number; nickname: string | null; schoolName: string | null; blockedAt: string }
export type BlockPage = { items: BlockedUser[]; nextCursor: string | null; hasNext: boolean }
const invalid = () => new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '서버 응답을 확인하지 못했어요. 목록을 다시 조회해 주세요.' })
const validCursor = (value: string) => /^[1-9]\d{0,18}$/.test(value) && (value.length < 19 || value <= '9223372036854775807')
function authenticated(token: string) {
  if (!token.trim()) throw new ApiError(401, { success: false, code: 'UNAUTHORIZED', message: '로그인 후 이용해 주세요.' })
}
export async function createReport(token: string, targetType: ReportTarget, targetId: number, reasonCode: ReportReason, detail: string, signal?: AbortSignal) {
  authenticated(token)
  if (!['USER', 'POST', 'APPLICATION'].includes(targetType) || !Number.isSafeInteger(targetId) || targetId <= 0 || !Object.hasOwn(reportReasons, reasonCode) || detail.trim().length > 200) throw new Error('신고 대상과 사유, 상세 내용 200자 제한을 확인해 주세요.')
  const value = await reportTarget(token, targetType, targetId, reasonCode, detail.trim(), signal)
  if (!value || !Number.isSafeInteger(value.reportId) || value.reportId <= 0 || value.status !== 'RECEIVED') throw invalid()
  return value
}
export async function getBlocks(token: string, cursor: string | null, signal: AbortSignal): Promise<BlockPage> {
  authenticated(token)
  if (cursor !== null && !validCursor(cursor)) throw new Error('차단 목록 커서를 확인해 주세요.')
  const query = new URLSearchParams({ size: '20' }); if (cursor) query.set('cursor', cursor)
  const value = await apiRequest<BlockPage>(`/api/users/me/blocks?${query}`, { token, signal, cache: 'no-store' })
  if (!value || !Array.isArray(value.items) || typeof value.hasNext !== 'boolean' || !(value.nextCursor === null || typeof value.nextCursor === 'string' && validCursor(value.nextCursor)) || value.hasNext && (!value.nextCursor || !value.items.length || cursor !== null && BigInt(value.nextCursor) >= BigInt(cursor)) || !value.hasNext && value.nextCursor !== null || value.items.some(x => !x || !Number.isSafeInteger(x.userId) || x.userId <= 0 || !(x.nickname === null || typeof x.nickname === 'string') || !(x.schoolName === null || typeof x.schoolName === 'string') || typeof x.blockedAt !== 'string')) throw invalid()
  return value
}
export function safetyError(error: unknown, operation: 'report' | 'blocks' = 'report') {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = { ALREADY_REPORTED: '이미 신고한 대상이에요.', UNAUTHORIZED: '로그인이 만료됐어요. 다시 로그인해 주세요.', UNVERIFIED: '학교 인증 후 신고할 수 있어요.', SUSPENDED: '이용이 정지된 계정은 신고할 수 없어요.', FORBIDDEN: '이 작업을 수행할 권한이 없어요.', CANNOT_REPORT_SELF: '본인이나 본인의 글은 신고할 수 없어요.', NOT_FOUND: '신고 대상을 찾을 수 없어요.' }
    return messages[error.code] ?? error.message
  }
  return operation === 'report' ? '서버 응답을 확인하지 못했어요. 신고는 접수됐을 수 있으며 다시 보내면 중복 여부를 확인합니다.' : '차단 목록 또는 해제 결과를 확인하지 못했어요. 목록을 새로고침해 상태를 확인해 주세요.'
}
