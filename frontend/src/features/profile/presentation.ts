import { ApiError } from '../../api/client'
import { authErrors } from '../auth/api'
import type { ItemType, Profile, Visibility } from './api'

export const itemLabels: Record<ItemType, string> = {
  EXPERIENCE: '경력·활동',
  AWARD: '수상 이력',
  CERTIFICATE: '자격증·교육',
  PORTFOLIO: '포트폴리오 링크',
}

export const experienceLabels: Record<string, string> = {
  CLUB: '동아리', INTERN: '인턴', PROJECT: '프로젝트', ETC: '기타',
}

export const visibilityLabels: Record<Visibility, string> = {
  PUBLIC: '전체 공개', APPLIED_LEADER: '지원한 팀장에게만',
}

export function profileError(error: unknown) {
  if (error instanceof ApiError && error.status === 401) return '로그인이 만료됐어요. 다시 로그인해 주세요.'
  const feedback = authErrors(error)
  return feedback.message ?? (Object.values(feedback.fields ?? {}).join(' ') || '잠시 후 다시 시도해 주세요.')
}

export function schoolName(profile: Profile) {
  return profile.school ? [profile.school.name, profile.school.campus].filter(Boolean).join(' ') : '학교 미등록'
}

export function portfolioLink(url: string | null | undefined) {
  if (!url || !/^https?:\/\//i.test(url)) return undefined
  try {
    const parsed = new URL(url ?? '')
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') return parsed.href
  } catch {
    return undefined
  }
}
