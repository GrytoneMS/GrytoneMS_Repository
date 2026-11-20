import { ApiError, apiRequest } from '../../api/client'
import type { PostRequestOptions } from './api'
import type { ApplicationDraft, ApplyResponse } from './application-model'

export async function applyToPost(postId: number, draft: ApplicationDraft, options: PostRequestOptions = {}) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '인증 연동 대기 중' })
  const result = await apiRequest<ApplyResponse>(`/api/posts/${postId}/applications`, {
    ...options, method: 'POST', body: draft,
  })
  if (!result || !Number.isSafeInteger(result.id) || result.id <= 0 || result.status !== 'PENDING') {
    throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '지원 응답을 확인할 수 없어요. 내 지원 내역을 먼저 확인해 주세요.' })
  }
  return result
}
