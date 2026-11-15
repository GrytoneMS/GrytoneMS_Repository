import { ApiError, apiRequest } from '../../api/client'
import type { PostRequestOptions } from './api'

function requireToken(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '공통 인증 연동 전에는 요청을 보내지 않습니다.' })
}

/** POST-04: 성공 응답은 data: null. 실제 API에는 mock 전환이 없습니다. */
export async function closePost(postId: number, options: PostRequestOptions = {}): Promise<void> {
  requireToken(options)
  const result = await apiRequest<unknown>(`/api/posts/${postId}/close`, { ...options, method: 'PATCH' })
  if (result !== null) throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '마감 결과를 확인할 수 없어요.' })
}

export function safeContactLink(value: unknown): string | null {
  if (typeof value !== 'string' || !value.trim() || value.length > 500 || [...value].some(char => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) return null
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) && !!url.hostname && !url.username && !url.password ? url.href : null
  } catch { return null }
}

/** APPL-07: 연락 수단은 이 별도 응답에서만 받습니다. */
export async function getPostContact(postId: number, options: PostRequestOptions = {}): Promise<string> {
  requireToken(options)
  const result = await apiRequest<{ contactLink: unknown }>(`/api/posts/${postId}/contact`, options)
  const link = safeContactLink(result?.contactLink)
  if (!link) throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '안전한 연락 수단 주소를 확인할 수 없어요.' })
  return link
}
