import { ApiError } from '../../api/client'
import type { AdminService } from './api'
import { allowedActions, jobError, type Report, type School, type Verification } from './model'
export const adminExampleEnabled = (query: URLSearchParams) => import.meta.env.DEV && import.meta.env.VITE_ADMIN_MOCK === 'true' && query.get('adminExample') === 'true'
export function createMockAdmin(query: URLSearchParams): AdminService {
  if (!adminExampleEnabled(query)) throw new Error('관리자 개발 예시를 명시적으로 활성화해야 합니다.')
  const outcome = query.get('adminResult') ?? 'success', viewer = query.get('adminViewer') ?? 'admin'
  const empty = query.get('adminCase') === 'empty'
  const error = (status: number, code: string): never => { throw new ApiError(status, { success: false, code, message: '개발 예시 오류' }) }
  const wait = (signal: AbortSignal) => new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, 180)
    if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true })
  })
  let changed = false, sequence = 100
  const now = new Date().toISOString()
  const schools: School[] = empty ? [] : [{ id: 1, name: '개발 예시 학교', campus: '예시 캠퍼스', emailDomain: 'demo.example', region: 'CHEONAN', active: true }]
  const reports: Report[] = empty ? [] : Array.from({ length: 24 }, (_, i) => ({ id: 50 - i, targetType: (['POST', 'USER', 'APPLICATION'] as const)[i % 3], targetId: 100 + i,
    target: { title: `개발 예시 대상 ${i + 1}`, content: '실제 사용자·모집글이 아닌 관리자 동작 확인용 내용입니다.', authorId: 200 + i, hidden: i % 3 === 0, deleted: false },
    reasonCode: ['FAKE', 'SPAM', 'ABUSE'][i % 3], detail: '개발 예시 신고 상세', reporter: { id: 300 + i, nickname: `예시 신고자 ${i + 1}` }, status: 'RECEIVED', action: null, pendingCountForTarget: 1, createdAt: now, handledAt: null }))
  const verifications: Verification[] = empty ? [] : Array.from({ length: 3 }, (_, i) => ({ id: i + 1, user: { id: i + 10, nickname: `예시 신청자 ${i + 1}`, email: `demo${i + 1}@example.invalid` }, school: { id: 1, name: '개발 예시 학교', campus: null }, status: 'PENDING', documentUrl: `/api/admin/verifications/${i + 1}/document`, rejectReason: null, requestedAt: now, reviewedAt: null }))
  const suspended = new Set<number>()
  return {
    async account(signal) { await wait(signal); if (viewer === 'guest') error(401, 'UNAUTHORIZED'); return { id: 999, nickname: '개발 예시 관리자', role: viewer === 'user' ? 'USER' : 'ADMIN' } },
    async list(menu, status, cursor, signal) {
      await wait(signal)
      if (outcome === 'loadFailure' || changed && outcome === 'refreshFailure') error(500, 'INTERNAL_ERROR')
      if (menu === 'schools') return { items: schools.filter(s => s.active).map(s => ({ ...s })), hasNext: false, nextCursor: null }
      const source = menu === 'verifications' ? verifications : reports
      const rows = source.filter(r => (!status || r.status === status) && (!cursor || (menu === 'verifications' && status === 'PENDING' ? r.id > Number(cursor) : r.id < Number(cursor))))
        .sort((a, b) => menu === 'verifications' && status === 'PENDING' ? a.id - b.id : b.id - a.id)
      const items = rows.slice(0, 5).map(r => JSON.parse(JSON.stringify(r)))
      return { items, hasNext: rows.length > 5, nextCursor: rows.length > 5 ? String(items.at(-1)!.id) : null }
    },
    async document(id, signal) {
      await wait(signal); if (!verifications.some(v => v.id === id && v.status === 'PENDING')) error(404, 'NOT_FOUND')
      return new Blob(['<svg xmlns="http://www.w3.org/2000/svg" width="700" height="400"><rect width="700" height="400" fill="#eff3fa"/><text x="60" y="180" font-size="28">DEVELOPMENT EXAMPLE DOCUMENT</text><text x="60" y="240" font-size="20">No personal data. Not a real certificate.</text></svg>'], { type: 'image/svg+xml' })
    },
    async submit(job, signal) {
      await wait(signal); if (viewer !== 'admin') error(403, 'FORBIDDEN')
      const validation = jobError(job); if (validation) error(400, 'INVALID_INPUT')
      if (outcome === 'failure') error(500, 'INTERNAL_ERROR')
      if (outcome === 'expired') error(401, 'UNAUTHORIZED')
      if (outcome === 'forbidden') error(403, 'FORBIDDEN')
      if (job.kind === 'resolve') {
        const r = reports.find(r => r.id === job.id); if (!r || !allowedActions(r).includes(job.action)) error(409, 'REPORT_ALREADY_RESOLVED')
        if (job.action === 'SUSPEND') { if (suspended.has(r!.target!.authorId)) error(409, 'ALREADY_SUSPENDED'); suspended.add(r!.target!.authorId) }
        reports.filter(other => other.targetId === r!.targetId && other.targetType === r!.targetType).forEach(other => { other.status = 'RESOLVED'; other.action = job.action; other.handledAt = now; other.pendingCountForTarget = 0; if (other.target && other.targetType === 'POST') { if (job.action === 'NONE') other.target.hidden = false; if (job.action === 'HIDE') other.target.hidden = true; if (job.action === 'DELETE') other.target.deleted = true } })
      } else if (job.kind === 'approve' || job.kind === 'reject') {
        const v = verifications.find(v => v.id === job.id); if (!v || v.status !== 'PENDING') error(409, 'VERIFICATION_ALREADY_REVIEWED')
        v!.status = job.kind === 'approve' ? 'APPROVED' : 'REJECTED'; v!.rejectReason = job.kind === 'reject' ? job.reason : null; v!.documentUrl = null; v!.reviewedAt = now
      } else if (job.kind === 'suspend') {
        if (suspended.has(job.id)) error(409, 'ALREADY_SUSPENDED'); suspended.add(job.id)
      } else if (job.kind === 'deactivateSchool') {
        const s = schools.find(s => s.id === job.id); if (!s) error(404, 'SCHOOL_NOT_FOUND'); s!.active = false
      } else if (job.kind === 'createSchool' || job.kind === 'updateSchool') {
        if (schools.some(s => s.emailDomain === job.school.emailDomain.trim().toLowerCase() && s.id !== job.id)) error(409, 'SCHOOL_DOMAIN_DUPLICATED')
        if (job.kind === 'createSchool') schools.push({ ...job.school, id: ++sequence, active: true })
        else { const s = schools.find(s => s.id === job.id); if (!s) error(404, 'SCHOOL_NOT_FOUND'); Object.assign(s!, { name: job.school.name, campus: job.school.campus, emailDomain: job.school.emailDomain }) }
      }
      changed = true
      if (outcome === 'lost') throw new TypeError('Development response loss')
    },
  }
}
