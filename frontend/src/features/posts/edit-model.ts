import { ApiError, apiRequest } from '../../api/client'
import { getPost, type PostRequestOptions } from './api'
import { getApplicants } from '../applicants/api'
import type { ApplicantRole } from '../applicants/model'
import type { PostDetail } from './model'
import { initialPostDraft, toCreatePostRequest, validatePostDraft, type PostDraft } from './create-model'

export type EditSnapshot = { post: PostDetail; contactLink: string; applicants: ApplicantRole[] }
export type RoleRule = { codeLocked: boolean; min: number; deletionReason?: string }
export function requireEditToken(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '로그인이 필요해요. 토큰 없는 조회·수정 요청은 보내지 않으며 입력은 유지됩니다.' })
}
function invalid(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '수정에 필요한 기존 값을 모두 확인하지 못했어요. 저장하지 않고 다시 조회해 주세요.' }) }
export function checkSnapshot(snapshot: EditSnapshot, id: string) {
  const { post, contactLink, applicants } = snapshot
  if (!post || String(post.id) !== id || typeof post.isMine !== 'boolean') invalid()
  if (!post.isMine) throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '본인 모집글만 수정할 수 있어요.' })
  if (!['OPEN', 'CLOSED'].includes(post.status) || typeof contactLink !== 'string' || !contactLink.trim()
    || ['title', 'description', 'periodStart', 'periodEnd', 'deadline', 'category', 'meetingType'].some(key => typeof post[key as keyof PostDetail] !== 'string')
    || ['locationCode', 'locationText', 'meetingFreq'].some(key => post[key as keyof PostDetail] !== null && typeof post[key as keyof PostDetail] !== 'string')
    || !Array.isArray(post.roles) || !post.roles.length || !Array.isArray(applicants) || applicants.length !== post.roles.length) invalid()
  const ids = new Set<number>()
  for (const role of post.roles) {
    if (!role || !Number.isSafeInteger(role.id) || role.id <= 0 || ids.has(role.id) || typeof role.roleCode !== 'string' || typeof role.roleName !== 'string'
      || !Number.isInteger(role.headcount) || !Number.isInteger(role.filledCount) || role.requirements !== null && typeof role.requirements !== 'string') invalid()
    ids.add(role.id)
    const matches = applicants.filter(item => item.postRoleId === role.id)
    if (matches.length !== 1 || matches[0].headcount !== role.headcount || matches[0].filledCount !== role.filledCount || !Array.isArray(matches[0].applications)) invalid()
  }
  if (post.event !== null && (!post.event || ['name', 'host', 'officialUrl', 'applyDeadline'].some(key => typeof post.event![key as keyof NonNullable<PostDetail['event']>] !== 'string'))) invalid()
  if (['CONTEST', 'COMPETITION'].includes(post.category) && !post.event) invalid()
  return snapshot
}
export async function loadEditSnapshot(id: string, options: PostRequestOptions = {}): Promise<EditSnapshot> {
  requireEditToken(options)
  if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) throw new ApiError(404, { success: false, code: 'NOT_FOUND', message: '올바른 모집글 주소가 아니에요.' })
  const post = await getPost(id, options)
  if (!post?.isMine) throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '본인 모집글만 수정할 수 있어요.' })
  const [contact, applicants] = await Promise.all([
    apiRequest<{ contactLink: string }>(`/api/posts/${id}/contact`, options), getApplicants(id, options),
  ])
  // URL 정규화도 CONTACT_CHANGED 알림을 만들 수 있어 원문을 그대로 보존합니다.
  return checkSnapshot({ post, contactLink: contact?.contactLink, applicants }, id)
}
export function editDraft(snapshot: EditSnapshot): PostDraft {
  const p = snapshot.post
  return { ...initialPostDraft(), title: p.title, category: p.category, description: p.description,
    periodStart: p.periodStart, periodEnd: p.periodEnd, deadline: p.deadline, meetingType: p.meetingType,
    locationCode: p.locationCode ?? '', locationText: p.locationText ?? '', meetingFreq: p.meetingFreq ?? '',
    contactLink: snapshot.contactLink, event: p.event ? { ...p.event } : initialPostDraft().event,
    roles: p.roles.map(r => ({ key: r.id, roleCode: r.roleCode, roleName: r.roleName, headcount: String(r.headcount), requirements: r.requirements ?? '' })),
  }
}
export function roleRules(snapshot: EditSnapshot): Record<number, RoleRule> {
  return Object.fromEntries(snapshot.post.roles.map(role => {
    const active = snapshot.applicants.find(item => item.postRoleId === role.id)!.applications.some(item => item.status !== 'CANCELED')
    return [role.id, { codeLocked: active, min: active ? role.headcount : 1,
      deletionReason: active ? '지원 이력이 있는 기존 역할은 삭제할 수 없어요.' : '취소된 지원 이력은 조회 API에서 제외되어 삭제 가능 여부를 확인할 수 없어요. 기존 역할은 보존합니다.' }]
  }))
}
export function validateEditDraft(draft: PostDraft, snapshot: EditSnapshot, today?: string) {
  const errors = validatePostDraft(draft, today)
  // 서버는 링크 필수·길이만 검사합니다. 기존 비표준 링크도 그대로 유지할 수 있습니다.
  for (const [field, value] of [['contactLink', draft.contactLink], ['event.officialUrl', draft.event.officialUrl]]) {
    if (value.trim() && value.length <= 500) delete errors[field]
  }
  const rules = roleRules(snapshot)
  const seen = new Set<number>()
  draft.roles.forEach((role, index) => {
    if (seen.has(role.key)) errors.roles = '역할 ID가 중복되었어요.'
    seen.add(role.key)
    const rule = rules[role.key], original = snapshot.post.roles.find(item => item.id === role.key)
    if (!original && role.key >= 0) errors.roles = '알 수 없는 기존 역할입니다.'
    if (rule?.codeLocked && original?.roleCode !== role.roleCode) errors[`roles[${index}].roleCode`] = '지원 이력이 있어 역할 분류를 바꿀 수 없어요.'
    if (rule && Number(role.headcount) < rule.min) errors[`roles[${index}].headcount`] = `지원 이력이 있어 ${rule.min}명 미만으로 줄일 수 없어요.`
  })
  if (snapshot.post.roles.some(role => !seen.has(role.id))) errors.roles = '기존 역할의 지원 관계를 보존하기 위해 삭제할 수 없어요.'
  return errors
}
export function toEditRequest(draft: PostDraft, snapshot: EditSnapshot) {
  const request = toCreatePostRequest(draft)
  const initial = editDraft(snapshot)
  for (const field of ['locationCode', 'locationText', 'meetingFreq'] as const) {
    if (draft.meetingType === initial.meetingType && draft.locationCode === initial.locationCode && draft[field] === initial[field]) {
      Object.assign(request, { [field]: snapshot.post[field] })
    }
  }
  return { ...request, roles: request.roles.map((role, index) => {
    const original = snapshot.post.roles.find(item => item.id === draft.roles[index].key)
    return { ...role, id: original?.id ?? null, requirements: original && draft.roles[index].requirements === (original.requirements ?? '') ? original.requirements : role.requirements }
  }) }
}
export async function updatePost(id: number, body: ReturnType<typeof toEditRequest>, options: PostRequestOptions = {}) {
  requireEditToken(options)
  // 성공 envelope를 받은 뒤 응답 상세의 부가 검증 실패로 재저장을 유도하지 않습니다. 이동 후 GET으로 확인합니다.
  return apiRequest<PostDetail>(`/api/posts/${id}`, { ...options, method: 'PUT', body })
}
export function editFailure(error: unknown, operation: 'load' | 'save' = 'save') {
  if (!(error instanceof ApiError)) return { message: operation === 'load' ? '수정에 필요한 정보를 불러오지 못했어요. 저장할 수 없습니다. 다시 조회해 주세요.' : '서버 응답을 확인하지 못했어요. 입력은 유지됩니다. 저장됐을 수 있으니 상세에서 확인한 뒤 다시 시도해 주세요.', fields: {} }
  const messages: Record<string, string> = {
    FORBIDDEN: '본인 글만 수정할 수 있어요. 권한이 변경되었을 수 있습니다.', NOT_FOUND: '모집글을 찾을 수 없거나 숨김·삭제된 글이에요.',
    POST_DELETED: '삭제된 모집글이에요.', UNAUTHORIZED: '로그인이 필요하거나 만료됐어요. 입력은 유지됩니다.',
    ROLE_HAS_APPLICANTS: '지원 현황이 변경되었어요. 역할 삭제·분류 변경·인원 감소가 제한됩니다. 입력은 유지되니 상세의 최신 현황을 확인해 주세요.',
    SUSPENDED: '이용 정지 상태에서는 수정할 수 없어요.',
  }
  const fields = Array.isArray(error.fieldErrors) ? Object.fromEntries(error.fieldErrors.map(item => [item.field, item.message])) : error.fieldErrors ?? {}
  return { message: messages[error.code] ?? error.message, fields }
}
