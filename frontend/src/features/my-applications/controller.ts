import { actionFor, requestFailure, type Action, type ActionResult, type ApplicationPage, type Filter, type MyApplication } from './model'

export type MyApplicationsService = { load: (status: Filter, cursor: string | undefined, signal: AbortSignal) => Promise<ApplicationPage>; act: (id: number, action: Action, signal: AbortSignal) => Promise<ActionResult> }
export type ListState = { items: MyApplication[]; loading: boolean; moreLoading: boolean; loaded: boolean; busyId: number | null; hasNext: boolean; nextCursor: string | null; error: ReturnType<typeof requestFailure> | null; moreError: string; notice: string; needsRefresh: boolean }
export const initialListState = (): ListState => ({ items: [], loading: true, moreLoading: false, loaded: false, busyId: null, hasNext: false, nextCursor: null, error: null, moreError: '', notice: '', needsRefresh: false })
export function createMyApplicationsController(service: MyApplicationsService, status: Filter, change: (state: ListState) => void) {
  let state = initialListState(), disposed = false, generation = 0, mutation = false
  let query: AbortController | null = null, actionRequest: AbortController | null = null
  const confirmed = new Map<number, ActionResult['status']>()
  function update(patch: Partial<ListState>) { if (!disposed) { state = { ...state, ...patch }; change(state) } }
  function reconciled(page: ApplicationPage) {
    if (page.items.some(item => confirmed.has(item.id) && confirmed.get(item.id) !== item.status)) throw new Error('처리 결과가 목록에 아직 반영되지 않았습니다.')
    return page
  }
  async function load(afterAction = false) {
    if (disposed || mutation && !afterAction) return
    query?.abort(); query = new AbortController()
    const current = ++generation, signal = query.signal
    update({ loading: true, moreLoading: false, moreError: '', error: null })
    try {
      const page = reconciled(await service.load(status, undefined, signal))
      if (disposed || generation !== current) return
      update({ items: page.items, hasNext: page.hasNext, nextCursor: page.nextCursor, loaded: true, needsRefresh: false })
    } catch (error) {
      if (disposed || generation !== current) return
      const failure = requestFailure(error, confirmed.size ? 'refresh' : 'load')
      update({ error: failure, needsRefresh: state.needsRefresh || state.loaded, ...(failure.access ? { items: [], hasNext: false, nextCursor: null } : {}) })
    } finally { if (generation === current) update({ loading: false }) }
  }
  return {
    load: () => load(),
    async more() {
      if (disposed || mutation || state.loading || state.moreLoading || state.needsRefresh || !state.hasNext || !state.nextCursor) return
      query?.abort(); query = new AbortController()
      const current = ++generation, cursor = state.nextCursor, signal = query.signal
      update({ moreLoading: true, moreError: '' })
      try {
        const page = reconciled(await service.load(status, cursor, signal))
        if (disposed || current !== generation) return
        const seen = new Set(state.items.map(item => item.id))
        update({ items: [...state.items, ...page.items.filter(item => !seen.has(item.id))], nextCursor: page.nextCursor, hasNext: page.hasNext })
      } catch (error) {
        if (disposed || current !== generation) return
        const failure = requestFailure(error, 'more')
        update(failure.access ? { items: [], hasNext: false, nextCursor: null, error: failure, needsRefresh: true } : { moreError: failure.message })
      } finally { if (current === generation) update({ moreLoading: false }) }
    },
    async act(id: number, action: Action) {
      if (disposed || mutation || state.loading || state.moreLoading || state.needsRefresh) return
      const item = state.items.find(row => row.id === id)
      if (!item || actionFor(item) !== action) return
      mutation = true; query?.abort(); ++generation
      actionRequest = new AbortController()
      update({ busyId: id, error: null, moreError: '', notice: '' })
      try {
        const result = await service.act(id, action, actionRequest.signal)
        if (disposed) return
        confirmed.set(id, result.status)
        update({ items: state.items.map(row => row.id === id ? { ...row, status: result.status } : row), needsRefresh: true,
          notice: action === 'cancel' ? '지원 취소가 완료됐어요. 같은 모집글에는 다시 지원할 수 없습니다.' : '팀 나가기가 완료됐어요. 연락 수단 접근 권한이 종료됩니다.' })
        await load(true)
      } catch (error) {
        if (disposed) return
        const failure = requestFailure(error, 'action')
        update({ error: failure, needsRefresh: true, ...(failure.access ? { items: [], hasNext: false, nextCursor: null } : {}) })
      } finally { mutation = false; update({ busyId: null }) }
    },
    dispose() { disposed = true; ++generation; query?.abort(); actionRequest?.abort(); state = initialListState() },
  }
}
