import { apiRequest } from '../../api/client'
import type { AuthStore } from '../auth/session'

export type Notification = {
  id: number
  type: string
  targetType: 'POST' | 'APPLICATION' | 'REPORT' | 'VERIFICATION' | null
  targetId: number | null
  message: string
  read: boolean
  createdAt: string
}

export type NotificationPage = {
  items: Notification[]
  nextCursor: string | null
  hasNext: boolean
}

export function notificationApi(store: AuthStore) {
  const request = <T,>(path: string, signal: AbortSignal, method = 'GET') =>
    store.execute(({ token }, authSignal) => apiRequest<T>(path, {
      token, method, signal: AbortSignal.any([signal, authSignal]), cache: 'no-store',
    }))

  return {
    list: (cursor: string | null, signal: AbortSignal) => request<NotificationPage>(
      cursor ? `/api/notifications?cursor=${encodeURIComponent(cursor)}` : '/api/notifications', signal,
    ),
    count: async (signal: AbortSignal) => (await request<{ count: number }>('/api/notifications/unread-count', signal)).count,
    read: (id: number, signal: AbortSignal) => request<unknown>(`/api/notifications/${id}/read`, signal, 'PATCH'),
    readAll: (signal: AbortSignal) => request<unknown>('/api/notifications/read-all', signal, 'PATCH'),
  }
}

export function notificationExample(): ReturnType<typeof notificationApi> {
  const base = { targetType: 'POST' as const, targetId: 1, createdAt: new Date().toISOString() }
  const items: Notification[] = [
    { ...base, id: 6, type: 'NEW_APPLICATION', message: '영상 편집 역할에 새 지원이 도착했어요.', read: false },
    { ...base, id: 5, type: 'ACCEPTED', message: '팀에 합류했어요. 연락 수단을 확인해 보세요.', read: false },
    { ...base, id: 4, type: 'SCRAP_DEADLINE_SOON', message: '스크랩한 모집이 내일 마감돼요.', read: false },
    { ...base, id: 3, type: 'MEMBER_LEFT', message: '팀원이 나가서 다시 자리가 열렸어요.', read: true },
    { ...base, id: 2, type: 'REVERIFY_REQUIRED', targetType: null, targetId: null, message: '이번 학기 학교 인증을 갱신해 주세요.', read: true },
    { ...base, id: 1, type: 'REPORT_RESOLVED', targetType: null, targetId: null, message: '신고하신 내용이 처리됐어요.', read: true },
  ]
  return {
    list: async () => ({ items: items.map(item => ({ ...item })), nextCursor: null, hasNext: false }),
    count: async () => items.filter(item => !item.read).length,
    read: async id => { const item = items.find(entry => entry.id === id); if (item) item.read = true },
    readAll: async () => { items.forEach(item => { item.read = true }) },
  }
}

const labels: Record<string, string> = {
  NEW_APPLICATION: '새 지원', APPLICATION_REMINDER: '지원자 확인', DEADLINE_SOON: '모집 마감 D-1',
  MEMBER_LEFT: '팀 나가기', ACCEPTED: '수락', REJECTED: '지원 결과', POST_CLOSED: '모집 마감',
  CONTACT_CHANGED: '연락 수단 변경', SCRAP_DEADLINE_SOON: '스크랩 마감 D-1',
  REVERIFY_REQUIRED: '재인증', REPORT_RESOLVED: '신고 처리', VERIFY_RESULT: '학교 인증 결과',
  POST_HIDDEN: '모집글 숨김', POST_RESTORED: '모집글 복구', POST_DELETED: '모집글 삭제',
  MEMBER_REMOVED: '팀 참여 종료',
}

export function notificationLabel(type: string) {
  return labels[type] ?? '알림'
}

export function notificationDestination({ type, targetType, targetId }: Notification) {
  if (type === 'REVERIFY_REQUIRED' || targetType === 'VERIFICATION') return '/school-verification'
  if (!targetId || !Number.isSafeInteger(targetId) || targetId < 1) return null
  if (targetType === 'APPLICATION') return '/me/applications'
  if (targetType !== 'POST') return null
  const post = `/posts/${targetId}`
  return ['NEW_APPLICATION', 'APPLICATION_REMINDER', 'MEMBER_LEFT'].includes(type) ? `${post}/applicants` : post
}
