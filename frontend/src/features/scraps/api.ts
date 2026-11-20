import { ApiError, apiRequest } from '../../api/client'
import type { PostRequestOptions } from '../posts/api'
import type { PostSummary } from '../posts/model'

export type Scrap = { scrapId: number; scrappedAt: string; post: PostSummary }
export type ScrapPage = { items: Scrap[]; nextCursor: string | null; hasNext: boolean }
export type ScrapResult = { postId: number; isScrapped: boolean }
export type ScrapService = {
  load: (cursor: string | undefined, signal: AbortSignal) => Promise<ScrapPage>
  change: (postId: number, saved: boolean, signal: AbortSignal) => Promise<ScrapResult>
  read: (postId: number, signal: AbortSignal) => Promise<ScrapResult>
}
const id = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v > 0
const long = (v: unknown): v is string => typeof v === 'string' && /^[1-9]\d*$/.test(v) && (v.length < 19 || v.length === 19 && v <= '9223372036854775807')
function invalid(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '스크랩 응답을 확인하지 못했어요. 목록을 다시 조회해 주세요.' }) }
function auth(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '공통 인증 연동 대기 중입니다. 토큰 없는 스크랩 요청은 보내지 않습니다.' })
}
export function validateScraps(page: ScrapPage, cursor?: string): ScrapPage {
  if (!page || !Array.isArray(page.items) || typeof page.hasNext !== 'boolean' || !(page.nextCursor === null || long(page.nextCursor))
    || page.hasNext && (!page.items.length || !page.nextCursor || page.nextCursor === cursor || cursor && BigInt(page.nextCursor) >= BigInt(cursor))) invalid()
  const scraps = new Set<number>(), posts = new Set<number>()
  for (const row of page.items) {
    const p = row?.post
    if (!id(row?.scrapId) || scraps.has(row.scrapId) || typeof row.scrappedAt !== 'string' || Number.isNaN(Date.parse(row.scrappedAt))
      || cursor && BigInt(row.scrapId) >= BigInt(cursor) || scraps.size && row.scrapId >= [...scraps].at(-1)!
      || !p || !id(p.id) || posts.has(p.id) || p.isScrapped !== true || typeof p.title !== 'string'
      || !['OPEN', 'CLOSED'].includes(p.status) || !['CONTEST', 'COMPETITION', 'PROJECT', 'STUDY'].includes(p.category)
      || !['ONLINE', 'OFFLINE', 'HYBRID'].includes(p.meetingType) || !Number.isFinite(p.dDay) || typeof p.deadline !== 'string'
      || !p.leader || !id(p.leader.id) || !Array.isArray(p.roles) || p.roles.some(r => !r || typeof r.roleName !== 'string' || !Number.isInteger(r.remaining) || r.remaining < 0)) invalid()
    scraps.add(row.scrapId); posts.add(p.id)
  }
  if (page.hasNext && page.nextCursor !== String(page.items.at(-1)!.scrapId)) invalid()
  return page
}
export async function getMyScraps(cursor?: string, options: PostRequestOptions = {}): Promise<ScrapPage> {
  auth(options)
  if (cursor !== undefined && !long(cursor)) throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '스크랩 커서를 확인해 주세요.' })
  const query = new URLSearchParams({ size: '20' })
  if (cursor !== undefined) query.set('cursor', cursor)
  return validateScraps(await apiRequest<ScrapPage>(`/api/users/me/scraps?${query}`, options), cursor)
}
export async function changeScrap(postId: number, saved: boolean, options: PostRequestOptions = {}): Promise<ScrapResult> {
  auth(options)
  if (!id(postId)) throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '모집글 번호를 확인해 주세요.' })
  const result = await apiRequest<ScrapResult>(`/api/posts/${postId}/scrap`, { ...options, method: saved ? 'PUT' : 'DELETE' })
  if (!result || result.postId !== postId || result.isScrapped !== saved) invalid()
  return result
}
export const realScraps = (options: PostRequestOptions = {}): ScrapService => ({
  load: (cursor, signal) => getMyScraps(cursor, { ...options, signal }),
  change: (postId, saved, signal) => changeScrap(postId, saved, { ...options, signal }),
  async read(postId, signal) {
    auth(options)
    const post = await apiRequest<{ id: number; isScrapped: boolean }>(`/api/posts/${postId}`, { ...options, signal })
    if (!post || post.id !== postId || typeof post.isScrapped !== 'boolean') invalid()
    return { postId, isScrapped: post.isScrapped }
  },
})
export function scrapError(error: unknown) {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      AUTH_PENDING: '공통 인증 연동 대기 중입니다. 토큰 없는 스크랩 요청은 보내지 않습니다.',
      UNAUTHORIZED: '로그인이 필요하거나 만료됐어요. 다시 로그인한 뒤 확인해 주세요.',
      UNVERIFIED: '스크랩 등록에는 학교 인증이 필요해요. 기존 스크랩 해제는 로그인만으로 가능합니다.',
      CANNOT_SCRAP_OWN_POST: '내가 올린 모집글은 스크랩할 수 없어요.',
      NOT_FOUND: '삭제되었거나 숨김 처리된 모집글입니다. 목록을 새로고침해 주세요.',
      SUSPENDED: '이용이 정지된 계정입니다.',
      FORBIDDEN: '스크랩 접근 권한을 확인해 주세요.',
    }
    return messages[error.code] ?? '스크랩 요청에 실패했어요. 기존 표시를 유지합니다. 다시 조회한 뒤 시도해 주세요.'
  }
  return '스크랩 요청 결과를 확인하지 못했어요. 반영됐을 수 있으니 다시 조회한 뒤 시도해 주세요.'
}
