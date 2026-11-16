import { ApiError } from '../../api/client'
import type { AdminService } from './api'
import { allowedActions, jobError, type Account, type Job, type Menu, type Report, type Row, type Verification } from './model'

export type AdminState = { access: 'checking' | 'anonymous' | 'denied' | 'error' | 'ready'; account: Account | null;
  items: Row[]; cursor: string | null; hasNext: boolean; loading: boolean; loaded: boolean; busy: boolean; blocked: boolean;
  error: string; moreError: string; notice: string; document: Blob | null; documentId: number | null; documentLoading: boolean }
const initial = (): AdminState => ({ access: 'checking', account: null, items: [], cursor: null, hasNext: false, loading: false, loaded: false, busy: false, blocked: false, error: '', moreError: '', notice: '', document: null, documentId: null, documentLoading: false })
export function adminMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '서버 응답을 확인하지 못했습니다. 반영됐을 수 있으니 재전송하지 말고 조회로 확인해 주세요.'
  const messages: Record<string, string> = { AUTH_PENDING: '로그인이 필요합니다.', UNAUTHORIZED: '로그인이 만료됐습니다. 다시 로그인해 주세요.', FORBIDDEN: '관리자 권한 또는 대상 조치 권한이 없습니다.', SCHOOL_DOMAIN_DUPLICATED: '이미 등록된 이메일 도메인입니다.', ALREADY_SUSPENDED: '이미 정지된 사용자입니다. 다시 정지하지 마세요.', REPORT_ALREADY_RESOLVED: '이미 처리된 신고입니다. 목록을 갱신해 주세요.', VERIFICATION_ALREADY_REVIEWED: '이미 심사된 신청입니다. 목록을 갱신해 주세요.', NOT_FOUND: '대상을 찾을 수 없습니다. 목록을 갱신해 주세요.', SCHOOL_NOT_FOUND: '학교를 찾을 수 없습니다.', INVALID_INPUT: '입력값을 확인해 주세요.', REPORT_ACTION_NOT_SUPPORTED: '이 대상에 사용할 수 없는 조치입니다.' }
  const fields = Array.isArray(error.fieldErrors) ? error.fieldErrors.map(f => f.message) : Object.values(error.fieldErrors ?? {})
  return fields.length ? fields.join(' · ') : messages[error.code] ?? '요청을 완료하지 못했습니다. 입력을 유지했으니 상태를 조회한 뒤 확인해 주세요.'
}
export function createAdminController(service: AdminService, menu: Menu, status: string, current = () => true) {
  let state = initial(), active = true, generation = 0, documentGeneration = 0, request: AbortController | undefined, mutation: AbortController | undefined, photo: AbortController | undefined, completed = false
  const listeners = new Set<() => void>(), reviewed = new Set<number>(), targets = new Set<string>()
  let unknownSuspension = false
  const valid = () => active && current()
  const publish = (patch: Partial<AdminState>) => { if (valid()) { state = { ...state, ...patch }; listeners.forEach(fn => fn()) } }
  function clearDocument() { documentGeneration++; photo?.abort(); publish({ document: null, documentId: null, documentLoading: false }) }
  function denied(error: unknown) {
    if (!(error instanceof ApiError) || ![401, 403].includes(error.status)) return false
    request?.abort(); photo?.abort(); mutation?.abort(); generation++; documentGeneration++
    publish({ ...initial(), access: error.status === 401 ? 'anonymous' : 'denied', error: adminMessage(error) }); return true
  }
  async function load(more = false, after = false) {
    if (!valid() || state.access !== 'ready' || state.busy && !after || state.loading && more || more && (state.blocked || !state.hasNext || !state.cursor)) return false
    clearDocument(); request?.abort(); request = new AbortController(); const signal = request.signal, version = ++generation
    const cursor = more ? state.cursor! : undefined
    publish({ loading: true, error: '', moreError: '' })
    try {
      const page = await service.list(menu, status, cursor, signal)
      if (!valid() || version !== generation || signal.aborted) return false
      if (page.items.some(row => menu === 'verifications' ? reviewed.has(row.id) && (row as Verification).status === 'PENDING' : menu === 'reports' && targets.has(`${(row as Report).targetType}:${(row as Report).targetId}`) && (row as Report).status === 'RECEIVED')) throw new Error('stale result')
      publish({ items: more ? [...state.items, ...page.items.filter(row => !state.items.some(old => old.id === row.id))] : page.items, cursor: page.nextCursor, hasNext: page.hasNext, loaded: true, blocked: unknownSuspension,
        ...(unknownSuspension ? { error: '이용 정지 결과는 현재 신고 목록으로 확인할 수 없습니다. 정지 상태 조회 API가 없어 서버 측 확인이 필요합니다. 재전송하지 마세요.' } : {}) })
      completed = false; return true
    } catch (error) {
      if (!valid() || version !== generation || signal.aborted) return false
      if (denied(error)) return false
      const message = completed ? '처리는 완료됐지만 최신 목록 조회에 실패했습니다. 다시 처리하지 말고 조회만 재시도해 주세요.' : '목록 조회에 실패했습니다. 다시 조회해 주세요.'
      publish(more ? { moreError: message } : { error: message }); return false
    } finally { if (valid() && version === generation && !signal.aborted) publish({ loading: false }) }
  }
  return {
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } }, getSnapshot: () => state,
    async start() {
      if (!valid() || state.busy) return
      request?.abort(); request = new AbortController(); const signal = request.signal, version = ++generation
      clearDocument(); publish({ ...initial(), access: 'checking' })
      try {
        const account = await service.account(signal)
        if (!valid() || version !== generation || signal.aborted) return
        if (account.role !== 'ADMIN') { publish({ access: 'denied', error: '일반 사용자는 관리자 화면에 접근할 수 없습니다.' }); return }
        publish({ account, access: 'ready' }); await load()
      } catch (error) { if (valid() && version === generation && !signal.aborted && !denied(error)) publish({ access: 'error', error: '권한을 확인하지 못했습니다. 다시 시도해 주세요.' }) }
    },
    refresh: () => load(), more: () => load(true), clearDocument,
    async showDocument(id: number) {
      if (!valid() || state.access !== 'ready' || state.busy || !state.items.some(r => r.id === id && (r as Verification).status === 'PENDING' && (r as Verification).documentUrl)) return
      clearDocument(); photo = new AbortController(); const signal = photo.signal, version = documentGeneration
      publish({ documentLoading: true, error: '' })
      try { const blob = await service.document(id, signal); if (valid() && version === documentGeneration && !signal.aborted) publish({ document: blob, documentId: id }) }
      catch (error) { if (valid() && version === documentGeneration && !signal.aborted && !denied(error)) publish({ error: adminMessage(error) }) }
      finally { if (valid() && version === documentGeneration && !signal.aborted) publish({ documentLoading: false }) }
    },
    async submit(job: Job) {
      if (!valid() || state.access !== 'ready' || state.busy || state.loading || state.blocked) return false
      const error = jobError(job)
      if (error) { publish({ error }); return false }
      if (job.kind === 'resolve' && !state.items.some(r => r.id === job.id && allowedActions(r as Report).includes(job.action))) { publish({ error: '조치 가능한 접수 신고를 선택해 주세요.' }); return false }
      if ((job.kind === 'approve' || job.kind === 'reject') && !state.items.some(r => r.id === job.id && (r as Verification).status === 'PENDING')) { publish({ error: '대기 중인 신청이 아닙니다. 목록을 확인해 주세요.' }); return false }
      if (job.kind === 'suspend' && !state.items.some(r => r.id === job.reportId && (r as Report).target?.authorId === job.id)) { publish({ error: '조회된 신고 대상 작성자를 선택해 주세요.' }); return false }
      mutation = new AbortController(); const signal = mutation.signal
      publish({ busy: true, error: '', notice: '' }); completed = false
      try {
        await service.submit(job, signal)
        if (!valid() || signal.aborted) return false
        completed = true; clearDocument()
        if (job.kind === 'approve' || job.kind === 'reject') reviewed.add(job.id)
        if (job.kind === 'resolve') { const r = state.items.find(r => r.id === job.id) as Report; targets.add(`${r.targetType}:${r.targetId}`) }
        publish({ blocked: true, notice: '처리가 완료됐습니다. 최신 목록을 확인합니다.', items: state.items.filter(r => !(job.kind === 'approve' || job.kind === 'reject' || job.kind === 'resolve' || job.kind === 'deactivateSchool') || r.id !== job.id) })
        await load(false, true); return true
      } catch (error) {
        if (!valid() || signal.aborted) return false
        clearDocument()
        if (!denied(error)) {
          const uncertain = !(error instanceof ApiError) || error.status >= 500 || error.code === 'INVALID_RESPONSE'
          if (job.kind === 'suspend' && uncertain) unknownSuspension = true
          publish({ blocked: uncertain || error instanceof ApiError && error.status === 409, error: `${adminMessage(error)}${uncertain ? ' 성공 여부가 불명확합니다. 조회로 확인하기 전에는 재처리할 수 없습니다.' : ''}` })
        }
        return false
      } finally { if (valid() && !signal.aborted) publish({ busy: false }) }
    },
    resume() { active = true },
    dispose() { active = false; generation++; documentGeneration++; request?.abort(); photo?.abort(); mutation?.abort(); state = initial() },
  }
}
export type AdminController = ReturnType<typeof createAdminController>
