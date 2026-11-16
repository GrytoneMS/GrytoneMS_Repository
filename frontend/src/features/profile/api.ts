import { apiRequest } from '../../api/client'
import type { RoleCode } from '../posts/model'

export type ItemType = 'EXPERIENCE' | 'AWARD' | 'CERTIFICATE' | 'PORTFOLIO'
export type Visibility = 'PUBLIC' | 'APPLIED_LEADER'
export type ItemInput = {
  itemType: ItemType
  subType?: string | null
  title: string
  role?: string | null
  issuer?: string | null
  startDate?: string | null
  endDate?: string | null
  description?: string | null
  url?: string | null
  visibility: Visibility
}
export type ProfileItem = ItemInput & { id: number }
export type ProfileBasics = { major: string | null; grade: number | null; bio: string | null }
export type Profile = ProfileBasics & {
  id: number
  nickname: string
  verified: boolean
  school: { name: string; campus: string | null } | null
  profileImageUrl: string | null
  roleTags: RoleCode[]
  skills: string[]
  items: ProfileItem[]
  activities: {
    postId: number; postTitle: string; roleName: string
    periodStart: string; periodEnd: string; left: boolean
  }[]
  showActivity: boolean | null
  completeness: number | null
}

export function getMyProfile(token: string, signal?: AbortSignal) {
  return apiRequest<Profile>('/api/users/me/profile', { token, signal })
}

export function saveRoleTags(token: string, roleCodes: RoleCode[]) {
  return apiRequest<Profile>('/api/users/me/role-tags', { method: 'PUT', token, body: { roleCodes } })
}

export function saveBasics(token: string, body: ProfileBasics) {
  return apiRequest<Profile>('/api/users/me/profile', { method: 'PUT', token, body })
}

export function saveSkills(token: string, skills: string[]) {
  return apiRequest<Profile>('/api/users/me/skills', { method: 'PUT', token, body: { skills } })
}

export function getNicknameChange(token: string, signal: AbortSignal) {
  return apiRequest<{ nicknameChangeableAt: string | null }>('/api/users/me', { token, signal })
}

export function saveNickname(token: string, nickname: string) {
  return apiRequest<{ nickname: string; nicknameChangeableAt: string | null }>(
    '/api/users/me/nickname', { method: 'PATCH', token, body: { nickname } },
  )
}

export function saveImage(token: string, file?: File) {
  const body = file ? new FormData() : undefined
  if (file) body?.append('file', file)
  return apiRequest<{ profileImageUrl: string | null }>('/api/users/me/profile-image', {
    token, body, method: file ? 'PUT' : 'DELETE',
  })
}

export function saveItem(token: string, body: ItemInput, id?: number) {
  const path = '/api/users/me/profile-items'
  return apiRequest<ProfileItem>(id === undefined ? path : `${path}/${id}`, {
    token, body, method: id === undefined ? 'POST' : 'PUT',
  })
}

export function deleteItem(token: string, id: number) {
  return apiRequest<null>(`/api/users/me/profile-items/${id}`, { method: 'DELETE', token })
}

export function saveActivityVisibility(token: string, showActivity: boolean) {
  return apiRequest<unknown>('/api/users/me/activity-visibility', {
    method: 'PUT', token, body: { showActivity },
  })
}
