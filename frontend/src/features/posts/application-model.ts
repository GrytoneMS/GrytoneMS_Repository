import { ApiError } from '../../api/client'
import type { PostDetail, PostRole } from './model'

export type ApplicationDraft = { postRoleId: number | null; message: string; availableTime: string }
export type ApplicationErrors = Partial<Record<keyof ApplicationDraft, string>>
export type ApplyResponse = { id: number; status: 'PENDING' }
export const roleOpen = (role: PostRole) => role.status === 'OPEN' && role.filledCount < role.headcount
export function applicationBlock(post: PostDetail, now = Date.now()) {
  if (post.isMine) return '내가 올린 모집글에는 지원할 수 없어요'
  if (post.myApplication) return '지원 이력이 있는 모집글에는 다시 지원할 수 없어요'
  if (post.status !== 'OPEN' || now >= Date.parse(`${post.deadline}T23:59:59.999+09:00`)) return '모집이 마감됐어요'
  if (!post.roles.some(roleOpen)) return '지원 가능한 역할이 없어요'
  return ''
}
// Jakarta @Size는 trim 이전 문자열의 UTF-16 길이를 검사합니다. 공백 여부는 별도로 검사합니다.
export function validateApplication(draft: ApplicationDraft, post: PostDetail): ApplicationErrors {
  const errors: ApplicationErrors = {}
  if (!post.roles.some(role => role.id === draft.postRoleId && roleOpen(role))) errors.postRoleId = '지원 가능한 역할을 선택해 주세요'
  if (!draft.message.trim()) errors.message = '지원 메시지를 입력해 주세요'
  else if (draft.message.length < 20 || draft.message.length > 300) errors.message = '지원 메시지는 20~300자로 입력해 주세요'
  if (!draft.availableTime.trim()) errors.availableTime = '가능한 시간을 입력해 주세요'
  else if (draft.availableTime.length > 100) errors.availableTime = '가능한 시간은 100자 이하로 입력해 주세요'
  return errors
}
const messages: Record<string, string> = {
  UNAUTHORIZED: '로그인이 만료됐거나 필요해요. 인증 연동 후 다시 로그인해 주세요.',
  UNVERIFIED: '학교 인증이 필요해요. 학교 인증 화면 연동을 기다리고 있어요.',
  SUSPENDED: '이용이 정지된 계정은 지원할 수 없어요.',
  CANNOT_APPLY_OWN_POST: '내가 올린 모집글에는 지원할 수 없어요.',
  ALREADY_APPLIED: '이미 지원한 모집글이에요. 취소·거절·마감된 지원도 다시 지원할 수 없어요.',
  ROLE_CLOSED: '모집글 또는 선택한 역할의 모집이 마감됐어요.',
  ROLE_FULL: '선택한 역할은 모집 인원이 다 찼어요.',
  NOT_FOUND: '모집글을 찾을 수 없어요. 삭제·숨김·차단 등으로 접근할 수 없을 수 있어요.',
  BLOCKED_USER: '차단 관계인 사용자에게는 지원할 수 없어요.',
  FORBIDDEN: '이 모집글에 지원할 권한이 없어요.',
  AUTH_PENDING: '인증 연동 전이라 지원 요청을 보내지 않았어요. 입력 내용은 유지됩니다.',
}
export function applicationFailure(error: unknown) {
  const fields: ApplicationErrors = {}
  if (error instanceof ApiError) {
    const entries = Array.isArray(error.fieldErrors) ? error.fieldErrors.map(item => [item.field, item.message]) : Object.entries(error.fieldErrors ?? {})
    for (const [field, message] of entries) if (['postRoleId', 'message', 'availableTime'].includes(field)) fields[field as keyof ApplicationDraft] = message
    return { message: messages[error.code] ?? error.message, fields }
  }
  return { message: '서버 응답을 확인하지 못했어요. 입력은 유지됩니다. 내 지원 내역을 확인한 뒤 다시 시도해 주세요.', fields }
}
