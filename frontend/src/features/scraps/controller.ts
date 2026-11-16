import { ApiError } from '../../api/client'
import { scrapError, type Scrap, type ScrapService } from './api'

export type ScrapState = {
  items: Scrap[]; cursor: string | null; hasNext: boolean; loading: boolean; loaded: boolean
  error: string; moreError: string; notice: string
  values: Record<number, boolean>; busy: Record<number, boolean>; errors: Record<number, string>
  needsCheck: Record<number, boolean>
}
export function createScrapController(service: ScrapService, initialValues: Record<number, boolean> = {}) {
  let state: ScrapState = { items: [], cursor: null, hasNext: false, loading: false, loaded: false, error: '', moreError: '', notice: '', values: initialValues, busy: {}, errors: {}, needsCheck: {} }
  let generation = 0, lifecycle = 0, revision = 0, active = true, needsRefresh = false
  let request: AbortController | undefined
  const mutations = new Map<number, AbortController>(), listeners = new Set<() => void>()
  const publish = (patch: Partial<ScrapState>) => { state = { ...state, ...patch }; listeners.forEach(fn => fn()) }
  const cancelLoad = () => { generation++; request?.abort() }
  const denied = (error: unknown) => error instanceof ApiError && (error.status === 401 && error.code !== 'AUTH_PENDING' || ['SUSPENDED', 'FORBIDDEN'].includes(error.code))
  function clearAccess() {
    cancelLoad(); lifecycle++; revision++; needsRefresh = false; mutations.forEach(c => c.abort()); mutations.clear()
    publish({ items: [], cursor: null, hasNext: false, values: {}, busy: {}, errors: {}, needsCheck: {}, loaded: false, loading: false })
  }
  async function load(more = false, preserve = false, afterChange = false) {
    if (!active || more && (state.loading || !state.hasNext || !state.cursor) || mutations.size) return
    const cursor = more ? state.cursor! : undefined
    cancelLoad()
    const version = generation, controller = new AbortController()
    request = controller
    publish({ loading: true, error: '', moreError: '', ...(!more && !preserve ? { items: [], loaded: false, cursor: null, hasNext: false } : {}) })
    try {
      const page = await service.load(cursor, controller.signal)
      if (!active || version !== generation || controller.signal.aborted) return
      const items = page.items
      const values = { ...state.values }
      for (const row of items) values[row.post.id] = row.post.isScrapped
      publish({ values, items: more ? [...state.items, ...items.filter(row => !state.items.some(old => old.post.id === row.post.id))] : items, cursor: page.nextCursor, hasNext: page.hasNext, loaded: true })
    } catch (error) {
      if (!active || version !== generation || controller.signal.aborted) return
      if (denied(error)) { clearAccess(); publish({ error: scrapError(error) }); return }
      publish(more ? { moreError: scrapError(error) } : { error: afterChange ? `변경은 완료됐지만 최신 목록 조회에 실패했어요. 다시 처리하지 말고 목록만 다시 조회해 주세요. ${scrapError(error)}` : scrapError(error) })
    } finally {
      if (active && version === generation && !controller.signal.aborted) publish({ loading: false })
    }
  }
  async function change(postId: number, initial: boolean, own = false) {
    if (!active || mutations.has(postId) || state.needsCheck[postId]) return
    const saved = state.values[postId] ?? initial
    if (own && !saved) { publish({ errors: { ...state.errors, [postId]: '내가 올린 모집글은 스크랩할 수 없어요.' } }); return }
    const controller = new AbortController(), version = lifecycle
    mutations.set(postId, controller)
    publish({ busy: { ...state.busy, [postId]: true }, errors: { ...state.errors, [postId]: '' }, notice: '' })
    try {
      const result = await service.change(postId, !saved, controller.signal)
      if (!active || lifecycle !== version || controller.signal.aborted) return
      revision++
      needsRefresh = needsRefresh || state.loaded || state.loading
      cancelLoad()
      publish({ values: { ...state.values, [postId]: result.isScrapped }, items: result.isScrapped ? state.items : state.items.filter(row => row.post.id !== postId), loading: false, notice: result.isScrapped ? '스크랩에 저장했어요.' : '스크랩을 해제했어요.' })
    } catch (error) {
      if (!active || lifecycle !== version || controller.signal.aborted) return
      if (denied(error)) { clearAccess(); publish({ error: scrapError(error) }) }
      publish({ errors: { ...state.errors, [postId]: scrapError(error) }, needsCheck: { ...state.needsCheck, [postId]: !(error instanceof ApiError) || error.code === 'INVALID_RESPONSE' } })
    } finally {
      if (active && lifecycle === version && !controller.signal.aborted) {
        mutations.delete(postId)
        publish({ busy: { ...state.busy, [postId]: false } })
        if (needsRefresh && mutations.size === 0) { needsRefresh = false; await load(false, true, true) }
      }
    }
  }
  async function recheck(postId: number) {
    if (!active || mutations.has(postId)) return
    const controller = new AbortController(), version = lifecycle
    mutations.set(postId, controller); publish({ busy: { ...state.busy, [postId]: true } })
    try {
      const result = await service.read(postId, controller.signal)
      if (!active || lifecycle !== version || controller.signal.aborted) return
      revision++
      cancelLoad()
      publish({ loading: false, values: { ...state.values, [postId]: result.isScrapped }, items: result.isScrapped ? state.items : state.items.filter(row => row.post.id !== postId), needsCheck: { ...state.needsCheck, [postId]: false }, errors: { ...state.errors, [postId]: '' } })
      needsRefresh = state.loaded
    } catch (error) {
      if (!active || lifecycle !== version || controller.signal.aborted) return
      if (denied(error)) clearAccess()
      publish({ errors: { ...state.errors, [postId]: scrapError(error) } })
    } finally {
      if (active && lifecycle === version && !controller.signal.aborted) {
        mutations.delete(postId); publish({ busy: { ...state.busy, [postId]: false } })
        if (needsRefresh && mutations.size === 0) { needsRefresh = false; await load(false, true) }
      }
    }
  }
  return {
    subscribe: (fn: () => void) => { listeners.add(fn); return () => { listeners.delete(fn) } },
    getRevision: () => revision,
    observe: (posts: { id: number; isScrapped: boolean }[], readRevision: number) => {
      if (!active || revision !== readRevision) return
      const values = { ...state.values }
      for (const post of posts) if (typeof post.isScrapped === 'boolean') values[post.id] = post.isScrapped
      publish({ values })
    },
    getSnapshot: () => state, first: () => load(), more: () => load(true), refresh: () => load(false, true), change, recheck,
    cancelList: () => { cancelLoad(); publish({ loading: false }) },
    resume: () => { active = true },
    dispose: () => { active = false; lifecycle++; needsRefresh = false; cancelLoad(); mutations.forEach(c => c.abort()); mutations.clear(); state = { ...state, loading: false, busy: {} } },
  }
}
