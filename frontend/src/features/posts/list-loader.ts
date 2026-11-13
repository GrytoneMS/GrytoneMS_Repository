import type { PostPage, PostSummary } from './model'
import { postErrorMessage } from './errors'

export type ListState = { items: PostSummary[]; nextCursor: string | null; hasNext: boolean; loading: boolean; error: string; moreError: string }
type FetchPage = (cursor: string | undefined, signal: AbortSignal) => Promise<PostPage>
// React와 분리해 조건 교체·언마운트 뒤 늦은 응답과 중복 더 보기 요청을 차단합니다.
export function createListLoader(fetchPage: FetchPage) {
  let state: ListState = { items: [], nextCursor: null, hasNext: false, loading: true, error: '', moreError: '' }
  let version = 0
  let controller: AbortController | undefined
  const listeners = new Set<() => void>()
  const publish = (patch: Partial<ListState>) => { state = { ...state, ...patch }; listeners.forEach(listener => listener()) }
  const cancel = () => { version++; controller?.abort(); controller = undefined }
  async function request(more: boolean) {
    if (more && (state.loading || !state.hasNext || !state.nextCursor)) return
    const cursor = more ? state.nextCursor! : undefined
    cancel()
    const current = version
    const active = new AbortController()
    controller = active
    publish(more ? { loading: true, moreError: '' } : { items: [], nextCursor: null, hasNext: false, loading: true, error: '', moreError: '' })
    try {
      const page = await fetchPage(cursor, active.signal)
      if (current !== version || active.signal.aborted) return
      publish({ items: more ? [...state.items, ...page.items.filter(item => !state.items.some(existing => existing.id === item.id))] : page.items, nextCursor: page.nextCursor, hasNext: page.hasNext })
    } catch (error) {
      if (current === version && !active.signal.aborted) publish(more ? { moreError: postErrorMessage(error) } : { error: postErrorMessage(error) })
    } finally {
      if (current === version && !active.signal.aborted) publish({ loading: false })
    }
  }
  return {
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    getSnapshot: () => state,
    first: () => request(false), more: () => request(true), cancel,
  }
}
