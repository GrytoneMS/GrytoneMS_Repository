import { ApiError } from '../../api/client'
import { actionError } from '../posts/post-actions-controller'
import type { PostStatus } from '../posts/model'
import { canDelete, type MyPage, type MyPost } from './api'

export type MyService = { load: (status: PostStatus, cursor: string | undefined, signal: AbortSignal) => Promise<MyPage>; close: (id: number, signal: AbortSignal) => Promise<void>; remove: (id: number, signal: AbortSignal) => Promise<void> }
export type MyState = { status: PostStatus; items: MyPost[]; cursor: string | null; hasNext: boolean; loading: boolean; more: boolean; busy: number | null; error: string; moreError: string; notice: string; needsRefresh: boolean }
export const myError = (error: unknown) => error instanceof ApiError && error.code === 'POST_HAS_APPLICANTS' ? '지원자가 있어 삭제할 수 없어요. 최신 목록을 다시 확인해 주세요.' : actionError(error)
export function createMyPosts(service: MyService, initial: PostStatus = 'OPEN') {
  let state: MyState = { status: initial, items: [], cursor: null, hasNext: false, loading: false, more: false, busy: null, error: '', moreError: '', notice: '', needsRefresh: false }
  let alive = true, sequence = 0
  let request: AbortController | undefined
  let mutation: AbortController | undefined
  const listeners = new Set<() => void>()
  const closed = new Set<number>(), deleted = new Set<number>()
  const update = (patch: Partial<MyState>) => { if (alive) { state = { ...state, ...patch }; listeners.forEach(fn => fn()) } }
  async function load(more = false) {
    if (!alive || state.busy !== null || more && (state.loading || state.more || !state.hasNext || state.needsRefresh)) return
    request?.abort(); request = new AbortController()
    const signal = request.signal, generation = ++sequence, status = state.status, cursor = more ? state.cursor ?? undefined : undefined
    update(more ? { more: true, moreError: '' } : { loading: true, more: false, error: '', moreError: '' })
    try {
      const page = await service.load(status, cursor, signal)
      if (!alive || generation !== sequence || signal.aborted) return
      if (more && page.hasNext && page.nextCursor === cursor) throw new Error('커서가 진행되지 않습니다.')
      if (page.items.some(item => deleted.has(item.id) || item.status === 'OPEN' && closed.has(item.id))) throw new Error('처리 결과가 아직 목록에 반영되지 않았습니다.')
      const items = more ? [...state.items, ...page.items.filter(item => !state.items.some(old => old.id === item.id))] : page.items
      update({ items, cursor: page.nextCursor, hasNext: page.hasNext, needsRefresh: false })
    } catch (error) {
      if (!alive || generation !== sequence || signal.aborted) return
      const access = error instanceof ApiError && [401,403,404].includes(error.status)
      const message = error instanceof ApiError ? myError(error) : '내 모집글을 불러오지 못했어요. 다시 조회해 주세요.'
      update({ ...(more ? { moreError: message } : { error: state.notice ? '처리는 완료됐지만 목록을 갱신하지 못했어요. 다시 처리할 필요는 없습니다. 목록만 다시 조회해 주세요.' : message }), ...(access ? { items: [], cursor: null, hasNext: false } : {}) })
    } finally { if (alive && generation === sequence) update({ loading: false, more: false }) }
  }
  return {
    getSnapshot: () => state,
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } },
    load,
    tab(status: PostStatus) { if (state.busy !== null) return; sequence++; request?.abort(); update({ status, items: [], cursor: null, hasNext: false, error: '', moreError: '', notice: '', needsRefresh: false, loading: false, more: false }); void load() },
    async act(id: number, kind: 'close' | 'delete') {
      const post = state.items.find(item => item.id === id)
      if (!alive || !post || state.busy !== null || state.loading || state.needsRefresh || kind === 'close' && post.status !== 'OPEN' || kind === 'delete' && !canDelete(post)) return
      sequence++; request?.abort(); mutation = new AbortController(); const signal = mutation.signal
      update({ busy: id, more: false, error: '', moreError: '', notice: '' })
      try {
        await (kind === 'close' ? service.close(id, signal) : service.remove(id, signal))
        if (!alive || signal.aborted) return
        ;(kind === 'close' ? closed : deleted).add(id)
        update({ items: state.items.filter(item => item.id !== id), notice: kind === 'close' ? '모집을 마감했어요.' : '모집글을 삭제했어요.', needsRefresh: true })
      } catch (error) {
        if (!alive || signal.aborted) return
        if (error instanceof ApiError && error.code === 'POST_ALREADY_CLOSED') { closed.add(id); update({ items: state.items.filter(item => item.id !== id) }) }
        const authPending = error instanceof ApiError && error.code === 'AUTH_PENDING'
        update({ error: myError(error), needsRefresh: !authPending, ...(error instanceof ApiError && [401,403,404].includes(error.status) && !authPending ? { items: [], hasNext: false, cursor: null } : {}) })
        return
      } finally { update({ busy: null }) }
      await load()
    },
    dispose() { alive = false; sequence++; request?.abort(); mutation?.abort(); listeners.clear() },
  }
}
