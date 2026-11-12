/** 그려톤 API 명세서(1차), 공통 규칙 및 LIST-01·02 / LIST-05. contactLink는 포함하지 않습니다. */
export type PostStatus = 'OPEN' | 'CLOSED'
export type Category = 'CONTEST' | 'COMPETITION' | 'PROJECT' | 'STUDY'
export type MeetingType = 'OFFLINE' | 'ONLINE' | 'HYBRID'
export type LocationCode = 'CHEONAN_TERMINAL' | 'CHEONAN_ASAN_STN' | 'DUJEONG_STN' | 'ONYANG_STN' | 'CAMPUS' | 'CUSTOM'
export type RoleCode = 'PLANNING' | 'DESIGN' | 'DEV' | 'VIDEO' | 'MARKETING' | 'PRESENT' | 'ETC'
export type ApplicationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED' | 'CLOSED' | 'LEFT'
export type Viewer = 'guest' | 'other' | 'owner' | 'applied' | 'accepted' | 'rejected' | 'canceled' | 'applicationClosed' | 'left'
export type RoleSummary = { roleCode: RoleCode; roleName: string; remaining: number }
export type PostRole = { id: number; roleCode: RoleCode; roleName: string; headcount: number; filledCount: number; requirements: string | null; status: PostStatus }
export type Leader = { id: number; nickname: string | null; schoolName: string | null }
export type PostSummary = {
  id: number; title: string; category: Category; status: PostStatus; deadline: string; dDay: number;
  meetingType: MeetingType; leader: Leader; roles: RoleSummary[]; createdAt: string
}
export type PostDetail = Omit<PostSummary, 'roles' | 'leader'> & {
  description: string; periodStart: string; periodEnd: string;
  locationCode: LocationCode | null; locationText: string | null; meetingFreq: string | null;
  event: { name: string; host: string; applyDeadline: string; officialUrl: string } | null;
  roles: PostRole[]; leader: Leader & { major: string; profileImageUrl: string | null };
  isMine: boolean; myApplication: { id: number; postRoleId: number; status: ApplicationStatus } | null
}
export type PostPage = { items: PostSummary[]; nextCursor: string | null; hasNext: boolean }
export type Sort = 'LATEST' | 'DEADLINE'

export const categoryLabels: Record<Category, string> = { CONTEST: '공모전', COMPETITION: '대회', PROJECT: '단기 프로젝트', STUDY: '스터디' }
export const meetingLabels: Record<MeetingType, string> = { OFFLINE: '대면', ONLINE: '비대면', HYBRID: '혼합' }
export const locationLabels: Record<LocationCode, string> = { CHEONAN_TERMINAL: '천안터미널', CHEONAN_ASAN_STN: '천안아산역', DUJEONG_STN: '두정역', ONYANG_STN: '온양온천역', CAMPUS: '대학 캠퍼스', CUSTOM: '직접 입력' }
export const applicationLabels: Record<ApplicationStatus, string> = { PENDING: '지원 완료 · 대기 중', ACCEPTED: '지원 수락됨', REJECTED: '지원 거절됨', CANCELED: '지원 취소됨', CLOSED: '지원 마감됨', LEFT: '팀에서 나감' }

export function locationLabel(post: PostDetail) {
  if (post.locationCode === 'CAMPUS' || post.locationCode === 'CUSTOM') return post.locationText || locationLabels[post.locationCode]
  return post.locationCode ? locationLabels[post.locationCode] : null
}

/** 동작은 이번 범위 밖입니다. 응답 상태만 정확하게 구분합니다. */
export function postActionLabel(post: PostDetail) {
  if (post.isMine) return '지원자 보기 · 준비 중'
  if (post.myApplication) return applicationLabels[post.myApplication.status]
  if (post.status === 'CLOSED') return '모집이 마감됐어요'
  if (post.roles.every(role => role.status === 'CLOSED' || role.filledCount >= role.headcount)) return '지원 가능한 역할이 없어요'
  return '지원하기 · 준비 중'
}
