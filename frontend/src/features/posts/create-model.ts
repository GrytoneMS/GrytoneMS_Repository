import { ApiError } from '../../api/client'
import { PostCreateAuthPendingError } from './create-integration'
import type { Category, LocationCode, MeetingType, RoleCode } from './model'

export const roleLabels: Record<RoleCode, string> = { PLANNING: '기획', DESIGN: '디자인', DEV: '개발', VIDEO: '영상', MARKETING: '마케팅', PRESENT: '발표', ETC: '기타' }
export type RoleDraft = { key: number; roleCode: RoleCode | ''; roleName: string; headcount: string; requirements: string }
export type PostDraft = {
  title: string; category: Category; description: string; periodStart: string; periodEnd: string; deadline: string;
  meetingType: MeetingType; locationCode: LocationCode | ''; locationText: string; meetingFreq: string;
  roles: RoleDraft[]; event: { name: string; host: string; officialUrl: string; applyDeadline: string }; contactLink: string
}
export type CreatePostRequest = {
  title: string; category: Category; description: string; periodStart: string; periodEnd: string; deadline: string;
  meetingType: MeetingType; locationCode: LocationCode | null; locationText: string | null; meetingFreq: string | null;
  roles: { roleCode: RoleCode; roleName: string; headcount: number; requirements: string | null }[];
  event: PostDraft['event'] | null; contactLink: string
}
export type CreateErrors = Record<string, string>
export function emptyRole(key: number): RoleDraft { return { key, roleCode: '', roleName: '', headcount: '1', requirements: '' } }
export function initialPostDraft(): PostDraft {
  return {
    title: '', category: 'CONTEST', description: '', periodStart: '', periodEnd: '', deadline: '',
    meetingType: 'OFFLINE', locationCode: '', locationText: '', meetingFreq: '', roles: [emptyRole(0)],
    event: { name: '', host: '', officialUrl: '', applyDeadline: '' }, contactLink: '',
  }
}
export function needsEvent(category: Category) { return category === 'CONTEST' || category === 'COMPETITION' }
export function needsLocationText(code: PostDraft['locationCode']) { return code === 'CAMPUS' || code === 'CUSTOM' }
export function todayInKorea() { return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' }) }
function isDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && value >= '0001-01-01' && value <= '9999-12-31'
    && !Number.isNaN(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value
}
function isHttpUrl(value: string) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && !!url.hostname }
  catch { return false }
}
export function validatePostDraft(draft: PostDraft, today = todayInKorea()): CreateErrors {
  const errors: CreateErrors = {}
  const text = (field: string, value: string, label: string, min: number, max: number) => {
    if (!value.trim()) errors[field] = `${label} 입력해 주세요.`
    else if (value.length < min || value.length > max) errors[field] = `${min}~${max.toLocaleString()}자로 입력해 주세요.`
  }
  text('title', draft.title, '제목을', 5, 40)
  if (!['CONTEST', 'COMPETITION', 'PROJECT', 'STUDY'].includes(draft.category)) errors.category = '유형을 선택해 주세요.'
  text('description', draft.description, '소개를', 20, 1000)
  for (const field of ['periodStart', 'periodEnd', 'deadline'] as const) {
    if (!isDate(draft[field])) errors[field] = '올바른 날짜를 입력해 주세요.'
  }
  if (!errors.periodStart && !errors.periodEnd && draft.periodEnd < draft.periodStart) errors.periodEnd = '활동 종료일은 시작일과 같거나 이후여야 해요.'
  if (!errors.deadline && draft.deadline < today) errors.deadline = '모집 마감일은 오늘 또는 이후여야 해요.'
  if (!errors.deadline && !errors.periodStart && draft.deadline > draft.periodStart) errors.deadline = '모집 마감일은 활동 시작일과 같거나 이전이어야 해요.'
  if (!['ONLINE', 'OFFLINE', 'HYBRID'].includes(draft.meetingType)) errors.meetingType = '진행 방식을 선택해 주세요.'
  if (draft.meetingType !== 'ONLINE') {
    if (!['CHEONAN_TERMINAL', 'CHEONAN_ASAN_STN', 'DUJEONG_STN', 'ONYANG_STN', 'CAMPUS', 'CUSTOM'].includes(draft.locationCode)) errors.locationCode = '모임 장소를 선택해 주세요.'
    if (needsLocationText(draft.locationCode)) text('locationText', draft.locationText, '장소를', 1, 100)
    text('meetingFreq', draft.meetingFreq, '모임 빈도를', 1, 30)
  }
  if (draft.roles.length < 1 || draft.roles.length > 5) errors.roles = '모집 역할은 1~5개 추가해 주세요.'
  draft.roles.forEach((role, index) => {
    const field = `roles[${index}]`
    if (!Object.hasOwn(roleLabels, role.roleCode)) errors[`${field}.roleCode`] = '역할 분류를 선택해 주세요.'
    text(`${field}.roleName`, role.roleName, '역할명을', 1, 30)
    if (!/^[1-5]$/.test(role.headcount)) errors[`${field}.headcount`] = '인원은 1~5 사이의 정수로 입력해 주세요.'
    if (role.requirements.length > 100) errors[`${field}.requirements`] = '요구 조건은 100자 이하로 입력해 주세요.'
  })
  if (needsEvent(draft.category)) {
    text('event.name', draft.event.name, '행사명을', 1, 100)
    text('event.host', draft.event.host, '주최를', 1, 100)
    text('event.officialUrl', draft.event.officialUrl, '공식 링크를', 1, 500)
    if (!errors['event.officialUrl'] && !isHttpUrl(draft.event.officialUrl)) errors['event.officialUrl'] = 'http:// 또는 https://로 시작하는 올바른 URL을 입력해 주세요.'
    if (!isDate(draft.event.applyDeadline)) errors['event.applyDeadline'] = '올바른 접수 마감일을 입력해 주세요.'
  }
  text('contactLink', draft.contactLink, '연락 링크를', 1, 500)
  if (!errors.contactLink && !isHttpUrl(draft.contactLink)) errors.contactLink = 'http:// 또는 https://로 시작하는 올바른 URL을 입력해 주세요.'
  return errors
}
/** 화면에서 숨긴 조건부 값은 전송하지 않습니다. draft와 응답 원본은 변경하지 않습니다. */
export function toCreatePostRequest(draft: PostDraft): CreatePostRequest {
  const offline = draft.meetingType !== 'ONLINE'
  return {
    title: draft.title, category: draft.category, description: draft.description,
    periodStart: draft.periodStart, periodEnd: draft.periodEnd, deadline: draft.deadline, meetingType: draft.meetingType,
    locationCode: offline ? draft.locationCode as LocationCode : null,
    locationText: offline && needsLocationText(draft.locationCode) ? draft.locationText : null,
    meetingFreq: offline ? draft.meetingFreq : null,
    roles: draft.roles.map(({ roleCode, roleName, headcount, requirements }) => ({
      roleCode: roleCode as RoleCode, roleName, headcount: Number(headcount), requirements: requirements.trim() ? requirements : null,
    })),
    event: needsEvent(draft.category) ? { ...draft.event } : null, contactLink: draft.contactLink,
  }
}
export function createFailure(error: unknown): { message: string; fields: CreateErrors; action?: 'login' | 'verify' } {
  if (error instanceof PostCreateAuthPendingError) return { message: error.message, fields: {} }
  if (!(error instanceof ApiError)) return { message: '등록 결과를 확인하지 못했어요. 연결 상태와 내 모집글을 확인한 뒤 다시 시도해 주세요.', fields: {} }
  const entries = Array.isArray(error.fieldErrors) ? error.fieldErrors.map(({ field, message }) => [field, message]) : Object.entries(error.fieldErrors ?? {})
  const fields = Object.fromEntries(entries)
  switch (error.code) {
    case 'UNAUTHORIZED': return { message: '로그인이 필요하거나 만료되었어요. 입력 내용은 유지됩니다.', fields, action: 'login' }
    case 'UNVERIFIED': return { message: '모집글을 등록하려면 학교 인증을 완료해 주세요. 입력 내용은 유지됩니다.', fields, action: 'verify' }
    case 'SUSPENDED': return { message: '이용이 정지된 계정은 모집글을 등록할 수 없어요.', fields }
    case 'POST_LIMIT_EXCEEDED': return { message: '모집 중인 글은 최대 3개예요. 기존 글을 마감한 뒤 다시 등록해 주세요.', fields }
    default: return { message: error.message || '등록하지 못했어요. 잠시 후 다시 시도해 주세요.', fields }
  }
}
