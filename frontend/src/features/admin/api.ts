import { ApiError, apiRequest } from '../../api/client'
import { actions, isCursor, isId, jobError, type Account, type Job, type Menu, type Page, type Report, type School, type Verification } from './model'

export type AdminService = {
  account: (signal: AbortSignal) => Promise<Account>
  list: (menu: Menu, status: string, cursor: string | undefined, signal: AbortSignal) => Promise<Page>
  submit: (job: Job, signal: AbortSignal) => Promise<void>
  document: (id: number, signal: AbortSignal) => Promise<Blob>
}
function invalid(): never { throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '응답을 확인하지 못했습니다. 처리 요청을 반복하지 말고 다시 조회해 주세요.' }) }
const text = (v: unknown) => typeof v === 'string'
const nullableText = (v: unknown) => v === null || text(v)
export function validateAccount(a: Account) {
  if (!a || !isId(a.id) || !['USER', 'ADMIN'].includes(a.role) || !nullableText(a.nickname)) invalid()
  return a
}
export function validateSchool(s: School) {
  if (!s || !isId(s.id) || !text(s.name) || !nullableText(s.campus) || !text(s.emailDomain) || !['CHEONAN', 'ASAN'].includes(s.region)) invalid()
  return s
}
export function validateReport(r: Report) {
  if (!r || !isId(r.id) || !isId(r.targetId) || !['POST', 'USER', 'APPLICATION'].includes(r.targetType) || !['RECEIVED', 'RESOLVED'].includes(r.status)
    || !text(r.reasonCode) || !nullableText(r.detail) || !r.reporter || !isId(r.reporter.id) || !nullableText(r.reporter.nickname)
    || !Number.isInteger(r.pendingCountForTarget) || r.pendingCountForTarget < 0 || !text(r.createdAt) || !nullableText(r.handledAt)
    || !(r.action === null || Object.hasOwn(actions, r.action))) invalid()
  if (r.target !== null && (!r.target || !nullableText(r.target.title) || !nullableText(r.target.content) || !isId(r.target.authorId) || typeof r.target.hidden !== 'boolean' || typeof r.target.deleted !== 'boolean')) invalid()
  return r
}
export function validateVerification(v: Verification) {
  if (!v || !isId(v.id) || !v.user || !isId(v.user.id) || !nullableText(v.user.email) || !nullableText(v.user.nickname) || !v.school || !isId(v.school.id) || !text(v.school.name) || !nullableText(v.school.campus)
    || !['PENDING', 'APPROVED', 'REJECTED'].includes(v.status) || !nullableText(v.rejectReason) || !text(v.requestedAt) || !nullableText(v.reviewedAt)
    || !(v.documentUrl === null || v.documentUrl === `/api/admin/verifications/${v.id}/document`)) invalid()
  return v
}
export function validatePage(page: Page, menu: Menu, cursor?: string, status = ''): Page {
  if (!page || !Array.isArray(page.items) || typeof page.hasNext !== 'boolean' || !(page.nextCursor === null || isCursor(page.nextCursor))
    || page.hasNext && (!page.items.length || !page.nextCursor || page.nextCursor === cursor) || !page.hasNext && page.nextCursor !== null) invalid()
  const ids = new Set<number>()
  let previous = cursor ? BigInt(cursor) : undefined
  const ascending = menu === 'verifications' && status === 'PENDING'
  for (const row of page.items) {
    if (!row || !isId(row.id) || ids.has(row.id)) invalid()
    if (menu !== 'schools' && previous !== undefined && (ascending ? BigInt(row.id) <= previous : BigInt(row.id) >= previous)) invalid()
    previous = BigInt(row.id); ids.add(row.id)
    if (menu === 'verifications') validateVerification(row as Verification)
    else if (menu === 'schools') validateSchool(row as School)
    else validateReport(row as Report)
  }
  if (page.hasNext && page.nextCursor !== String(page.items.at(-1)!.id)) invalid()
  return page
}
export function realAdmin(token: string | null, current = () => true, accountLoader?: (signal: AbortSignal) => Promise<Account>): AdminService {
  let verified = false
  function auth(admin = true) {
    if (!token?.trim() || !current()) throw new ApiError(401, { success: false, code: 'AUTH_PENDING', message: '로그인이 필요합니다.' })
    if (admin && !verified) throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '관리자 권한 확인이 필요합니다.' })
  }
  const request = <T,>(path: string, signal: AbortSignal, method = 'GET', body?: unknown) => {
    auth(); return apiRequest<T>(path, { token: token!, signal, method, body, cache: 'no-store' })
  }
  return {
    async account(signal) {
      verified = false; auth(false)
      const account = validateAccount(await (accountLoader ? accountLoader(signal) : apiRequest<Account>('/api/users/me', { token: token!, signal, cache: 'no-store' })))
      if (current() && !signal.aborted) verified = account.role === 'ADMIN'
      return account
    },
    async list(menu, status, cursor, signal) {
      auth()
      if (menu === 'schools') {
        const schools = await request<School[]>('/api/schools', signal)
        if (!Array.isArray(schools)) invalid()
        return validatePage({ items: schools, nextCursor: null, hasNext: false }, menu)
      }
      if (cursor !== undefined && !isCursor(cursor)) throw new Error('커서를 확인해 주세요.')
      const verification = menu === 'verifications'
      if (!(verification ? ['PENDING', 'APPROVED', 'REJECTED'] : ['', 'RECEIVED', 'RESOLVED']).includes(status)) throw new Error('조회 상태를 확인해 주세요.')
      const query = new URLSearchParams({ size: '20' })
      if (status) query.set('status', status)
      if (cursor) query.set('cursor', cursor)
      return validatePage(await request<Page>(`/api/admin/${verification ? 'verifications' : 'reports'}?${query}`, signal), menu, cursor, status)
    },
    async submit(job, signal) {
      auth(); const error = jobError(job); if (error) throw new ApiError(400, { success: false, code: 'INVALID_INPUT', message: error })
      if (job.kind === 'resolve') {
        if (!Object.hasOwn(actions, job.action)) invalid()
        const r = await request<{ reportId: number; action: string; resolvedCount: number }>(`/api/admin/reports/${job.id}/resolve`, signal, 'PATCH', { action: job.action, ...(job.action === 'SUSPEND' ? { suspendDays: job.days, suspendReason: job.reason.trim() } : {}) })
        if (!r || r.reportId !== job.id || r.action !== job.action || !Number.isInteger(r.resolvedCount) || r.resolvedCount < 1) invalid()
      } else if (job.kind === 'approve' || job.kind === 'reject') {
        const v = validateVerification(await request<Verification>(`/api/admin/verifications/${job.id}/${job.kind}`, signal, 'PATCH', job.kind === 'reject' ? { reason: job.reason.trim() } : undefined))
        if (v.id !== job.id || v.status !== (job.kind === 'approve' ? 'APPROVED' : 'REJECTED') || v.documentUrl !== null) invalid()
      } else if (job.kind === 'suspend') {
        const r = await request<{ userId: number; status: string; endsAt: string | null }>(`/api/admin/users/${job.id}/suspension`, signal, 'POST', { days: job.days, reason: job.reason.trim(), reportId: job.reportId })
        if (!r || r.userId !== job.id || r.status !== 'SUSPENDED' || !nullableText(r.endsAt)) invalid()
      } else if (job.kind === 'deactivateSchool') {
        const r = await request<null>(`/api/admin/schools/${job.id}/deactivate`, signal, 'PATCH'); if (r !== null) invalid()
      } else if (job.kind === 'createSchool' || job.kind === 'updateSchool') {
        const s = job.school
        const body = { name: s.name.trim(), campus: s.campus.trim(), emailDomain: s.emailDomain.trim().toLowerCase(), ...(job.kind === 'createSchool' ? { region: s.region } : {}) }
        const r = validateSchool(await request<School>(`/api/admin/schools${job.kind === 'createSchool' ? '' : `/${job.id}`}`, signal, job.kind === 'createSchool' ? 'POST' : 'PATCH', body))
        if (job.kind === 'updateSchool' && r.id !== job.id) invalid()
      }
    },
    async document(id, signal) {
      auth(); if (!isId(id)) invalid()
      const base = import.meta.env.VITE_API_BASE_URL?.trim(); if (!base) throw new Error('API 연결 설정이 필요합니다.')
      const response = await fetch(`${base.replace(/\/+$/, '')}/api/admin/verifications/${id}/document`, { headers: { Authorization: `Bearer ${token}`, Accept: 'image/png,image/jpeg' }, signal, cache: 'no-store', redirect: 'error' })
      if (!response.ok) throw new ApiError(response.status, { success: false, code: response.status === 401 ? 'UNAUTHORIZED' : response.status === 403 ? 'FORBIDDEN' : 'DOCUMENT_ERROR', message: '서류를 불러오지 못했습니다.' })
      if (!['image/png', 'image/jpeg'].includes(response.headers.get('Content-Type')?.split(';')[0] ?? '')) invalid()
      const blob = await response.blob(); if (!blob.size || blob.size > 15 * 1024 * 1024) invalid()
      return blob
    },
  }
}
