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
  return reportTarget(token, 'USER', userId, reasonCode, detail)
}

export function reportTarget(token: string, targetType: 'USER' | 'POST' | 'APPLICATION', targetId: number, reasonCode: keyof typeof reportReasons, detail: string, signal?: AbortSignal) {
  return apiRequest<{ reportId: number; status: string }>('/api/reports', {
    method: 'POST', token, signal, body: { targetType, targetId, reasonCode, detail },
  })
}

export function blockUser(token: string, userId: number, signal?: AbortSignal) {
  return apiRequest<{ isBlocked: boolean }>('/api/blocks', { method: 'POST', token, signal, body: { userId } })
}

export async function unblockUser(token: string, userId: string, signal?: AbortSignal) {
  if (!token.trim() || !/^[1-9]\d*$/.test(userId)) throw new Error('로그인과 차단 해제 대상을 확인해 주세요.')
  const value = await apiRequest<{ userId: number; isBlocked: boolean }>(`/api/blocks/${userId}`, { method: 'DELETE', token, signal })
  if (!value || String(value.userId) !== userId || value.isBlocked !== false) throw new Error('차단 해제 결과를 확인하지 못했어요. 목록을 다시 조회해 주세요.')
  return value
}
