import { apiRequest } from '../../api/client'
import type { Profile } from './api'

export const reportReasons = {
  ABUSE: '욕설·비하',
  FAKE: '허위 모집',
  SPAM: '홍보·스팸',
  PRIVACY: '개인정보 노출',
  ETC: '기타',
}

export function getUserProfile(token: string, userId: string, applicationId: string | null, signal: AbortSignal) {
  const path = applicationId ? `/api/applications/${applicationId}/profile` : `/api/users/${userId}/profile`
  return apiRequest<Profile>(path, { token, signal })
}

export function reportUser(token: string, userId: number, reasonCode: keyof typeof reportReasons, detail: string) {
  return apiRequest<{ reportId: number }>('/api/reports', {
    method: 'POST', token, body: { targetType: 'USER', targetId: userId, reasonCode, detail },
  })
}

export function blockUser(token: string, userId: number) {
  return apiRequest<{ isBlocked: boolean }>('/api/blocks', { method: 'POST', token, body: { userId } })
}

export function unblockUser(token: string, userId: string) {
  return apiRequest<{ isBlocked: boolean }>(`/api/blocks/${userId}`, { method: 'DELETE', token })
}
