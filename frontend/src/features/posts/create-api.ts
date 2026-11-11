import { ApiError, apiRequest } from '../../api/client'
import type { CreatePostRequest } from './create-model'
import type { PostRequestOptions } from './api'
import { PostCreateAuthPendingError } from './create-integration'

/** 실제 POST만 호출합니다. mock 모드나 요청 실패를 성공으로 대체하지 않습니다. */
export async function createPost(body: CreatePostRequest, options: PostRequestOptions = {}) {
  // 인증 컨텍스트가 연결될 때까지 토큰 없는 작성 요청은 fetch 전에 차단합니다.
  if (!options.token?.trim()) throw new PostCreateAuthPendingError()
  const result = await apiRequest<{ id: number }>('/api/posts', { ...options, method: 'POST', body })
  if (!result || !Number.isSafeInteger(result.id) || result.id <= 0) {
    throw new ApiError(201, { success: false, code: 'INVALID_RESPONSE', message: '등록 응답의 id를 확인하지 못했어요. 중복 등록을 피하려면 내 모집글을 먼저 확인해 주세요.' })
  }
  return result
}
