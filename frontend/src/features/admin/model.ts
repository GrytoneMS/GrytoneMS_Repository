export type Menu = 'reports' | 'verifications' | 'suspensions' | 'schools'
export const menus: Record<Menu, string> = { reports: '신고 처리', verifications: '서류 인증 승인', suspensions: '이용 정지', schools: '학교 관리' }
export type Action = 'NONE' | 'HIDE' | 'DELETE' | 'SUSPEND'
export const actions: Record<Action, string> = { NONE: '문제없음', HIDE: '숨김', DELETE: '삭제', SUSPEND: '이용 정지' }
export const reasons: Record<string, string> = { ABUSE: '욕설·비하', FAKE: '허위 모집', SPAM: '홍보·스팸', PRIVACY: '개인정보 노출', ETC: '기타' }
export type Account = { id: number; nickname: string | null; role: 'ADMIN' | 'USER' }
export type Report = { id: number; targetType: 'POST' | 'USER' | 'APPLICATION'; targetId: number;
  target: { title: string | null; content: string | null; authorId: number; hidden: boolean; deleted: boolean } | null;
  reasonCode: string; detail: string | null; reporter: { id: number; nickname: string | null }; status: 'RECEIVED' | 'RESOLVED';
  action: Action | null; pendingCountForTarget: number; createdAt: string; handledAt: string | null }
export type Verification = { id: number; user: { id: number; nickname: string | null; email: string | null }; school: { id: number; name: string; campus: string | null };
  status: 'PENDING' | 'APPROVED' | 'REJECTED'; documentUrl: string | null; rejectReason: string | null; requestedAt: string; reviewedAt: string | null }
export type School = { id: number; name: string; campus: string | null; emailDomain: string; region: 'CHEONAN' | 'ASAN'; active?: boolean }
export type SchoolInput = { name: string; campus: string; emailDomain: string; region: 'CHEONAN' | 'ASAN' }
export type Row = Report | Verification | School
export type Page = { items: Row[]; nextCursor: string | null; hasNext: boolean }
export type Job = { kind: 'resolve'; id: number; action: Action; days: number | null; reason: string }
  | { kind: 'approve' | 'reject'; id: number; reason: string }
  | { kind: 'suspend'; id: number; reportId: number; days: number | null; reason: string }
  | { kind: 'createSchool' | 'updateSchool'; id: number; school: SchoolInput }
  | { kind: 'deactivateSchool'; id: number }
export const isId = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
export const isCursor = (value: unknown): value is string => typeof value === 'string' && /^[1-9]\d*$/.test(value) && (value.length < 19 || value.length === 19 && value <= '9223372036854775807')
export function allowedActions(report: Report): Action[] {
  if (report.status !== 'RECEIVED') return []
  return report.targetType === 'POST' ? ['NONE', 'HIDE', 'DELETE', ...(report.target ? ['SUSPEND' as const] : [])] : ['NONE', ...(report.target ? ['SUSPEND' as const] : [])]
}
export function suspensionError(days: number | null, reason: string) {
  return ![7, 30, null].includes(days) ? '정지 기간을 선택해 주세요.' : !reason.trim() || reason.trim().length > 200 ? '사유는 1~200자로 입력해 주세요.' : ''
}
export function jobError(job: Job) {
  if (job.kind !== 'createSchool' && !isId(job.id)) return '대상 번호를 확인해 주세요.'
  if (job.kind === 'suspend' && !isId(job.reportId)) return '선택한 신고 번호를 확인해 주세요.'
  if (job.kind === 'suspend' || job.kind === 'resolve' && job.action === 'SUSPEND') return suspensionError(job.days, job.reason)
  if (job.kind === 'reject') return !job.reason.trim() || job.reason.trim().length > 200 ? '반려 사유는 1~200자로 입력해 주세요.' : ''
  if (job.kind === 'createSchool' || job.kind === 'updateSchool') {
    const s = job.school
    if (!s.name.trim() || s.name.trim().length > 50 || s.campus.trim().length > 30) return '학교명 1~50자, 캠퍼스 30자 이하로 입력해 주세요.'
    if (s.emailDomain.trim().length > 100 || !/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(s.emailDomain.trim())) return '이메일 도메인을 확인해 주세요.'
    if (!['CHEONAN', 'ASAN'].includes(s.region)) return '지역을 선택해 주세요.'
  }
  return ''
}
