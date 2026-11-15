import { ApiError, apiRequest } from '../../api/client'
import type { PostRequestOptions } from '../posts/api'
import type { PostStatus, Category, RoleCode } from '../posts/model'

export type MyRole = { id: number; roleCode: RoleCode; roleName: string; headcount: number; filledCount: number; status: PostStatus; applicantCount: number; pendingCount: number }
export type MyPost = { id: number; title: string; category: Category; status: PostStatus; deadline: string; dDay: number; applicantCount: number; pendingCount: number; roles: MyRole[]; createdAt: string; closedAt: string | null }
export type MyPage = { items: MyPost[]; nextCursor: string | null; hasNext: boolean }
export function requireMyToken(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '공통 인증 연동 전에는 요청을 보내지 않습니다.' })
}
const count = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
const id = (value: unknown) => count(value) && (value as number) > 0
function invalid(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '내 모집글 응답을 확인할 수 없어요.' }) }
export function validateMyPage(value: MyPage, status: PostStatus): MyPage {
  if (!value || !Array.isArray(value.items) || typeof value.hasNext !== 'boolean' || !(value.nextCursor === null || typeof value.nextCursor === 'string')) invalid()
  if (value.hasNext && (!value.items.length || !value.nextCursor || !/^[1-9]\d*$/.test(value.nextCursor) || BigInt(value.nextCursor) > 9223372036854775807n) || !value.hasNext && value.nextCursor !== null) invalid()
  const ids = new Set<number>()
  for (const item of value.items) {
    if (!item || !id(item.id) || ids.has(item.id) || typeof item.title !== 'string' || item.status !== status || !['CONTEST','COMPETITION','PROJECT','STUDY'].includes(item.category)
      || typeof item.deadline !== 'string' || !Number.isSafeInteger(item.dDay) || !count(item.applicantCount) || !count(item.pendingCount) || item.pendingCount > item.applicantCount
      || typeof item.createdAt !== 'string' || !(item.closedAt === null || typeof item.closedAt === 'string') || !Array.isArray(item.roles)) invalid()
    ids.add(item.id)
    const roleIds = new Set<number>()
    for (const role of item.roles) {
      if (!role || !id(role.id) || roleIds.has(role.id) || typeof role.roleName !== 'string' || !['PLANNING','DESIGN','DEV','VIDEO','MARKETING','PRESENT','ETC'].includes(role.roleCode)
        || !id(role.headcount) || !count(role.filledCount) || role.filledCount > role.headcount || !['OPEN','CLOSED'].includes(role.status)
        || !count(role.applicantCount) || !count(role.pendingCount) || role.pendingCount > role.applicantCount) invalid()
      roleIds.add(role.id)
    }
    if (item.roles.reduce((n, role) => n + role.applicantCount, 0) !== item.applicantCount || item.roles.reduce((n, role) => n + role.pendingCount, 0) !== item.pendingCount) invalid()
  }
  return value
}
export async function getMyPosts(status: PostStatus, cursor: string | undefined, options: PostRequestOptions = {}, size = 20): Promise<MyPage> {
  requireMyToken(options)
  if (!['OPEN','CLOSED'].includes(status) || !Number.isInteger(size) || size < 1 || size > 50 || cursor !== undefined && (!/^[1-9]\d*$/.test(cursor) || BigInt(cursor) > 9223372036854775807n)) throw new Error('잘못된 목록 조건입니다.')
  const query = new URLSearchParams({ status, size: String(size) }); if (cursor) query.set('cursor', cursor)
  return validateMyPage(await apiRequest<MyPage>(`/api/users/me/posts?${query}`, options), status)
}
export async function deleteMyPost(postId: number, options: PostRequestOptions = {}) {
  requireMyToken(options)
  if (!id(postId)) throw new Error('잘못된 모집글 번호입니다.')
  if (await apiRequest<unknown>(`/api/posts/${postId}`, { ...options, method: 'DELETE' }) !== null) invalid()
}
export const canDelete = (post: MyPost) => post.applicantCount === 0 && post.roles.every(role => role.applicantCount === 0)
export const deletionNote = (post: MyPost) => post.status === 'OPEN' ? '지원자가 있어 삭제할 수 없어요. 대신 마감할 수 있어요.' : '지원자가 있어 삭제할 수 없어요.'
