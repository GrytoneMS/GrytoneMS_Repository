import { ApiError } from '../../api/client'
import type { ApplicationStatus, PostDetail } from '../posts/model'

export type Applicant = { id: number; nickname: string | null; schoolName: string | null; major: string | null; grade: number | null; profileImageUrl: string | null }
export type Application = { id: number; status: ApplicationStatus; message: string; availableTime: string; createdAt: string; applicant: Applicant }
export type ApplicantRole = { postRoleId: number; roleName: string; headcount: number; filledCount: number; applications: Application[] }
export type Snapshot = { post: PostDetail; roles: ApplicantRole[] }
export type Decision = 'accept' | 'reject' | 'remove'
export type Reason = 'ROLE_MISMATCH' | 'SCHEDULE' | 'FILLED' | 'ETC'
export type DecisionResult = { applicationId: number; status: 'ACCEPTED' | 'REJECTED' | 'LEFT'; roleClosed?: boolean; postClosed?: boolean; roleReopened?: boolean; postReopened?: boolean }
export const reasonLabels: Record<Reason, string> = { ROLE_MISMATCH: '역할 조건 불일치', SCHEDULE: '일정 불일치', FILLED: '다른 지원자 선정', ETC: '기타' }
export const statusLabels: Record<ApplicationStatus, string> = { PENDING: '대기 중', ACCEPTED: '수락됨', REJECTED: '거절됨', CANCELED: '지원 취소', CLOSED: '지원 마감', LEFT: '팀에서 나감' }
export function findApplication(snapshot: Snapshot, id: number) {
  for (const role of snapshot.roles) { const application = role.applications.find(item => item.id === id); if (application) return { role, application } }
  return null
}
export function decisionBlock(snapshot: Snapshot, id: number, decision: Decision) {
  if (!snapshot.post.isMine) return '이 모집글의 팀장만 처리할 수 있어요.'
  const item = findApplication(snapshot, id)
  if (!item) return '지원 내역을 찾을 수 없어요.'
  if (decision === 'remove') return item.application.status === 'ACCEPTED' ? '' : '수락된 팀원만 내보낼 수 있어요.'
  if (item.application.status === 'CLOSED') return '모집 마감으로 종료된 지원입니다. 빈자리가 생겨 역할이 다시 열려도 이 지원은 다시 수락할 수 없어요.'
  if (item.application.status !== 'PENDING') return '이미 처리된 지원이에요.'
  if (decision === 'accept') {
    const role = snapshot.post.roles.find(role => role.id === item.role.postRoleId)
    if (!role || role.status !== 'OPEN' || item.role.filledCount >= item.role.headcount) return '이 역할은 마감됐거나 모집 인원이 다 찼어요.'
  }
  // 서버 accept는 역할 OPEN/정원, reject는 PENDING만 검사합니다. 글 상태/날짜 조건을 임의 추가하지 않습니다.
  return ''
}
export function applyDecision(snapshot: Snapshot, result: DecisionResult): Snapshot {
  const target = findApplication(snapshot, result.applicationId)!
  // 내보내기 성공 직후에는 인원·모집 상태를 추측하지 않고 서버 재조회를 기다립니다.
  if (result.status === 'LEFT') return { ...snapshot, roles: snapshot.roles.map(role => ({ ...role,
    applications: role.applications.map(item => item.id === result.applicationId ? { ...item, status: 'LEFT' } : item),
  })) }
  const accepted = result.status === 'ACCEPTED'
  return {
    post: { ...snapshot.post, status: result.postClosed ? 'CLOSED' : snapshot.post.status,
      roles: snapshot.post.roles.map(role => role.id === target.role.postRoleId && accepted ? { ...role, filledCount: role.filledCount + 1, status: result.roleClosed ? 'CLOSED' : role.status } : role) },
    roles: snapshot.roles.map(role => ({ ...role,
      filledCount: role.postRoleId === target.role.postRoleId && accepted ? role.filledCount + 1 : role.filledCount,
      applications: role.applications.map(item => item.id === result.applicationId ? { ...item, status: result.status }
        : role.postRoleId === target.role.postRoleId && result.roleClosed && item.status === 'PENDING' ? { ...item, status: 'CLOSED' } : item),
    })),
  }
}
export function managementFailure(error: unknown, operation: 'load' | 'refresh' | 'decide' = 'decide') {
  const queryMessage = operation === 'refresh'
    ? '처리는 완료됐지만 최신 목록을 불러오지 못했어요. 처리를 반복하지 말고 목록을 다시 조회해 주세요.'
    : '지원자 목록을 불러오지 못했어요. 목록을 다시 조회해 주세요.'
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      AUTH_PENDING: '로그인이 필요해요. 토큰 없는 관리 요청은 보내지 않았습니다.',
      UNAUTHORIZED: '로그인이 필요하거나 만료됐어요.', FORBIDDEN: '이 모집글의 팀장만 지원자를 관리할 수 있어요.',
      NOT_FOUND: '모집글 또는 지원 내역을 찾을 수 없어요.', SUSPENDED: '이용이 정지된 계정은 처리할 수 없어요.',
      UNVERIFIED: '학교 인증이 필요해요.', ROLE_FULL: '역할이 마감됐거나 정원이 찼어요. 최신 현황을 다시 확인해 주세요.',
      ROLE_CLOSED: '역할 모집이 마감됐어요. 최신 현황을 다시 확인해 주세요.',
      INVALID_APPLICATION_STATUS: '이미 처리된 지원이에요. 최신 목록을 다시 확인해 주세요.',
    }
    return { code: error.code, message: messages[error.code] ?? (operation !== 'decide' ? queryMessage : error.message),
      access: error.status === 401 || error.status === 403 || error.status === 404,
      reconcile: operation !== 'load' && (error.status >= 500 || ['ROLE_FULL', 'ROLE_CLOSED', 'INVALID_APPLICATION_STATUS', 'INVALID_RESPONSE'].includes(error.code)) }
  }
  return { code: operation === 'decide' ? 'NETWORK' : 'LOAD_FAILED', message: operation === 'decide' ? '서버 응답을 확인하지 못했어요. 처리됐을 수 있으니 목록을 다시 조회해 주세요.' : queryMessage, access: false, reconcile: operation !== 'load' }
}
