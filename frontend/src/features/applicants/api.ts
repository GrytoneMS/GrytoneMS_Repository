import { ApiError, apiRequest } from '../../api/client'
import { getPost, type PostRequestOptions } from '../posts/api'
import type { ApplicantRole, Decision, DecisionResult, Reason, Snapshot } from './model'
import { reasonLabels, statusLabels } from './model'

function requireToken(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '인증 연동 대기' })
}
function invalidResponse(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '서버 응답 형식이 올바르지 않아요. 처리 요청을 반복하지 말고 최신 목록을 확인해 주세요.' }) }
function validId(id: unknown) { return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 }
export function checkPostId(id: string) {
  if (!/^[1-9]\d*$/.test(id) || BigInt(id) > 9223372036854775807n) throw new ApiError(404, { success: false, code: 'NOT_FOUND', message: '올바른 모집글 주소가 아니에요.' })
}
export async function getApplicants(postId: string, options: PostRequestOptions = {}): Promise<ApplicantRole[]> {
  requireToken(options); checkPostId(postId)
  const roles = await apiRequest<ApplicantRole[]>(`/api/posts/${postId}/applications`, options)
  if (!Array.isArray(roles)) invalidResponse()
  const roleIds = new Set<number>(), applicationIds = new Set<number>()
  for (const role of roles) {
    if (!role || !validId(role.postRoleId) || roleIds.has(role.postRoleId) || typeof role.roleName !== 'string'
      || !Number.isInteger(role.headcount) || role.headcount < 1 || !Number.isInteger(role.filledCount) || role.filledCount < 0 || role.filledCount > role.headcount || !Array.isArray(role.applications)) invalidResponse()
    roleIds.add(role.postRoleId)
    for (const item of role.applications) {
      if (!item || !validId(item.id) || applicationIds.has(item.id) || !Object.hasOwn(statusLabels, item.status)
        || typeof item.message !== 'string' || typeof item.availableTime !== 'string' || typeof item.createdAt !== 'string'
        || !item.applicant || !validId(item.applicant.id)
        || [item.applicant.nickname, item.applicant.schoolName, item.applicant.major, item.applicant.profileImageUrl].some(value => value !== null && typeof value !== 'string')
        || !(item.applicant.grade === null || Number.isInteger(item.applicant.grade))) invalidResponse()
      applicationIds.add(item.id)
    }
  }
  return roles
}
export async function loadManagement(postId: string, options: PostRequestOptions = {}): Promise<Snapshot> {
  requireToken(options); checkPostId(postId)
  const post = await getPost(postId, options)
  if (!post || String(post.id) !== postId || typeof post.isMine !== 'boolean' || !Array.isArray(post.roles)) invalidResponse()
  if (!post.isMine) throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '팀장만 관리할 수 있어요.' })
  const roles = await getApplicants(postId, options)
  if (roles.length !== post.roles.length || roles.some(role => !post.roles.some(detail => detail.id === role.postRoleId && detail.headcount === role.headcount && detail.filledCount === role.filledCount))) invalidResponse()
  return { post, roles }
}
export async function decideApplication(applicationId: number, decision: Decision, reason: Reason | null = null, options: PostRequestOptions = {}): Promise<DecisionResult> {
  requireToken(options)
  if (!validId(applicationId) || !['accept', 'reject'].includes(decision) || reason !== null && !Object.hasOwn(reasonLabels, reason)) {
    throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '지원 번호와 처리 내용을 확인해 주세요.' })
  }
  const result = await apiRequest<DecisionResult>(`/api/applications/${applicationId}/${decision}`, {
    ...options, method: 'PATCH', ...(decision === 'reject' ? { body: { reason } } : {}),
  })
  if (!result || result.applicationId !== applicationId || result.status !== (decision === 'accept' ? 'ACCEPTED' : 'REJECTED')
    || decision === 'accept' && (typeof result.roleClosed !== 'boolean' || typeof result.postClosed !== 'boolean')) invalidResponse()
  return result
}
