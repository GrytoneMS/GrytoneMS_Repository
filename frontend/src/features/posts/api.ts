import { ApiError, apiRequest } from '../../api/client'
import type { PostDetail, PostPage, Sort } from './model'
import { appendPostFilters, keywordError, type PostFilters } from './list-query'

export type PostRequestOptions = { signal?: AbortSignal; token?: string }
export type ListPostsQuery = Partial<PostFilters> & { sort?: Sort; cursor?: string; size?: number }

export async function getPosts({ sort = 'LATEST', cursor, size = 20, ...filters }: ListPostsQuery = {}, options: PostRequestOptions = {}) {
  const error = keywordError(filters.keyword ?? '')
  if (error) throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: error })
  const query = new URLSearchParams({ sort, size: String(size) })
  appendPostFilters(query, filters)
  // 서버의 opaque cursor를 해석·재구성하지 않습니다. 첫 요청은 생략합니다.
  if (cursor !== undefined) query.set('cursor', cursor)
  const page = await apiRequest<PostPage>(`/api/posts?${query}`, options)
  // 잘못된 커서 응답으로 같은 페이지를 무한 요청하거나 더 보기가 멈추지 않도록 합니다.
  if (!page || !Array.isArray(page.items) || typeof page.hasNext !== 'boolean'
    || !(page.nextCursor === null || typeof page.nextCursor === 'string')
    || page.hasNext && (!page.nextCursor || page.nextCursor === cursor)) {
    throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '목록 페이지네이션 응답 형식이 올바르지 않습니다.' })
  }
  return page
}

export type SchoolOption = { id: number; name: string; campus: string | null; region: 'CHEONAN' | 'ASAN'; emailDomain: string }
export async function getSchools(options: PostRequestOptions = {}) {
  const schools = await apiRequest<SchoolOption[]>('/api/schools', options)
  if (!Array.isArray(schools) || schools.some(school => !school || !Number.isSafeInteger(school.id) || school.id <= 0 || typeof school.name !== 'string')) {
    throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '학교 목록 응답 형식이 올바르지 않습니다.' })
  }
  return schools
}

export function getPost(postId: string, options: PostRequestOptions = {}) {
  return apiRequest<PostDetail>(`/api/posts/${encodeURIComponent(postId)}`, options)
}
