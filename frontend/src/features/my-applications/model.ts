import { ApiError } from '../../api/client'
import type { ApplicationStatus } from '../posts/model'

export const statusLabels: Record<ApplicationStatus, string> = { PENDING: '대기', ACCEPTED: '수락', REJECTED: '거절', CANCELED: '취소', CLOSED: '마감', LEFT: '나감' }
export type Filter = ApplicationStatus | ''
export type Action = 'cancel' | 'leave'
export type Session = { userId: string; token: string }
// 미래 상태를 임의로 대기/수락으로 바꾸지 않습니다. 알 수 없는 상태도 읽기 전용으로 표시합니다.
export type MyApplication = { id: number; status: string; roleName: string; post: { id: number; title: string | null; deleted: boolean }; createdAt: string; decidedAt: string | null }
export type ApplicationPage = { items: MyApplication[]; hasNext: boolean; nextCursor: string | null }
export type ActionResult = { applicationId: number; status: 'CANCELED' | 'LEFT'; roleReopened?: boolean; postReopened?: boolean }
export function knownStatus(status: string): status is ApplicationStatus { return Object.hasOwn(statusLabels, status) }
export function statusLabel(status: string) { return knownStatus(status) ? statusLabels[status] : `알 수 없는 지원 상태 (${status})` }
export function readFilter(params: URLSearchParams): Filter {
  const value = params.get('status') ?? ''
  return knownStatus(value) ? value : ''
}
export function filterParams(previous: URLSearchParams, status: Filter) {
  const next = new URLSearchParams(previous)
  if (status) next.set('status', status); else next.delete('status')
  next.delete('cursor'); next.delete('size')
  return next
}
export function actionFor(item: MyApplication): Action | null {
  if (item.status === 'PENDING') return 'cancel'
  if (item.status === 'ACCEPTED') return 'leave'
  return null
}
export function applicationTitle(item: MyApplication) { return item.post.deleted ? '삭제된 모집글' : item.post.title ?? '제목을 확인할 수 없는 모집글' }
export function detailPath(item: MyApplication, mock = false) {
  if (item.post.deleted) return null
  const viewer = item.status === 'ACCEPTED' ? 'accepted' : item.status === 'REJECTED' ? 'rejected' : item.status === 'CANCELED' ? 'canceled' : item.status === 'CLOSED' ? 'applicationClosed' : item.status === 'LEFT' ? 'left' : item.status === 'PENDING' ? 'applied' : 'other'
  return `/posts/${item.post.id}${mock ? `?viewer=${viewer}` : ''}`
}
export function formatTimestamp(value: string) { return value.replace('T', ' ').slice(0, 16) }
export function requestFailure(error: unknown, operation: 'load' | 'more' | 'action' | 'refresh') {
  const base = { code: 'NETWORK', access: false }
  if (!(error instanceof ApiError)) return { ...base, message: operation === 'action'
    ? '처리 결과를 확인하지 못했어요. 이미 반영됐을 수 있으니 요청을 반복하지 말고 목록을 다시 조회해 주세요.'
    : operation === 'refresh' ? '처리는 완료됐지만 최신 목록을 불러오지 못했어요. 다시 처리할 필요 없이 목록만 다시 조회해 주세요.' : '지원 내역을 불러오지 못했어요. 연결을 확인한 뒤 다시 조회해 주세요.' }
  const messages: Record<string, string> = {
    AUTH_PENDING: '공통 인증 연동 대기 중입니다. 토큰 없는 실제 조회·처리 요청은 보내지 않습니다.',
    UNAUTHORIZED: '로그인이 필요하거나 만료됐어요. 이전 사용자의 지원 내역을 표시하지 않습니다.',
    FORBIDDEN: '본인의 지원만 처리할 수 있어요. 권한을 확인한 뒤 다시 조회해 주세요.',
    NOT_FOUND: '지원 내역을 찾을 수 없어요. 최신 목록을 다시 확인해 주세요.',
    INVALID_APPLICATION_STATUS: '지원 상태가 바뀌었어요. 수락·거절 등으로 더 이상 처리할 수 없으니 최신 목록을 조회해 주세요.',
    SUSPENDED: '이용이 정지된 계정입니다. 인증 상태를 확인해 주세요.',
  }
  return { code: error.code, access: error.status === 401 || error.status === 403,
    message: operation === 'refresh' ? '처리는 완료됐지만 최신 목록 조회에 실패했어요. 다시 처리하지 말고 목록만 재조회해 주세요.'
      : operation === 'action' && error.code === 'INVALID_RESPONSE' ? '처리 응답을 확인하지 못했어요. 이미 반영됐을 수 있으니 요청을 반복하지 말고 목록을 다시 조회해 주세요.' : messages[error.code] ?? error.message }
}
