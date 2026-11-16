import { ApiError, apiRequest } from '../../api/client'
import type { PostRequestOptions } from '../posts/api'
import { knownStatus, type Action, type ActionResult, type ApplicationPage, type Filter, type MyApplication } from './model'

function auth(options: PostRequestOptions) {
  if (!options.token?.trim()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '공통 인증 연동 대기 중' })
}
function invalid(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '서버 응답 형식이 올바르지 않아요. 목록을 다시 조회해 주세요.' }) }
const id = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const timestamp = (value: unknown) => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(value) && !Number.isNaN(Date.parse(value))
export function validatePage(value: ApplicationPage, cursor?: string, status: Filter = ''): ApplicationPage {
  if (!value || !Array.isArray(value.items) || typeof value.hasNext !== 'boolean' || !(value.nextCursor === null || typeof value.nextCursor === 'string')) invalid()
  const ids = new Set<number>()
  for (const row of value.items as MyApplication[]) {
    if (!row || !id(row.id) || ids.has(row.id) || typeof row.status !== 'string' || !row.status.trim() || typeof row.roleName !== 'string'
      || !row.post || !id(row.post.id) || typeof row.post.deleted !== 'boolean' || !(row.post.title === null || typeof row.post.title === 'string')
      || !row.post.deleted && row.post.title === null || !timestamp(row.createdAt) || !(row.decidedAt === null || timestamp(row.decidedAt))
      || status && row.status !== status) invalid()
    ids.add(row.id)
  }
  if (value.hasNext && (!value.items.length || !value.nextCursor || !/^[1-9]\d*$/.test(value.nextCursor)
    || BigInt(value.nextCursor) > 9223372036854775807n || value.nextCursor !== String(value.items.at(-1)!.id)
    || cursor !== undefined && BigInt(value.nextCursor) >= BigInt(cursor))) invalid()
  return value
}
export async function getMyApplications(status: Filter = '', cursor?: string, options: PostRequestOptions = {}) {
  auth(options)
  if (status && !knownStatus(status) || cursor !== undefined && (!/^[1-9]\d*$/.test(cursor) || BigInt(cursor) > 9223372036854775807n)) {
    throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '상태 또는 커서를 확인해 주세요.' })
  }
  const query = new URLSearchParams({ size: '20' })
  if (status) query.set('status', status)
  if (cursor !== undefined) query.set('cursor', cursor)
  return validatePage(await apiRequest<ApplicationPage>(`/api/users/me/applications?${query}`, options), cursor, status)
}
export async function actOnApplication(applicationId: number, action: Action, options: PostRequestOptions = {}): Promise<ActionResult> {
  auth(options)
  if (!id(applicationId) || !['cancel', 'leave'].includes(action)) throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: '지원 번호와 처리 내용을 확인해 주세요.' })
  const result = await apiRequest<ActionResult>(`/api/applications/${applicationId}/${action}`, { ...options, method: 'PATCH' })
  if (!result || result.applicationId !== applicationId || result.status !== (action === 'cancel' ? 'CANCELED' : 'LEFT')
    || action === 'leave' && (typeof result.roleReopened !== 'boolean' || typeof result.postReopened !== 'boolean')) invalid()
  return result
}
