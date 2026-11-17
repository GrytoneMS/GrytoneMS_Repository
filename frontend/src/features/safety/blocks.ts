import { ApiError } from '../../api/client'
import { safetyError, type BlockedUser, type BlockPage } from './api'

export type BlocksState = { items: BlockedUser[]; cursor: string | null; hasNext: boolean; loading: boolean; removing: number | null; error: string; notice: string; denied: boolean }
export const initialBlocks: BlocksState = { items: [], cursor: null, hasNext: false, loading: false, removing: null, error: '', notice: '', denied: false }
export function createBlocksController(publish: (state: BlocksState) => void, current: () => boolean, service: {
  load: (cursor: string | null, signal: AbortSignal) => Promise<BlockPage>;
  remove: (userId: number, signal: AbortSignal) => Promise<unknown>;
}) {
  let state = { ...initialBlocks }, active = true, version = 0, request = new AbortController()
  const valid = (v: number) => active && current() && v === version && !request.signal.aborted
  const update = (patch: Partial<BlocksState>) => { state = { ...state, ...patch }; publish(state) }
  function failure(error: unknown) {
    const denied = error instanceof ApiError && (error.status === 401 || error.status === 403)
    update({ error: safetyError(error, 'blocks'), denied, ...(denied ? { items: [], hasNext: false, cursor: null } : {}) })
  }
  async function load(more = false) {
    if (!active || !current() || state.loading || state.removing !== null || more && !state.hasNext) return
    request.abort(); request = new AbortController(); const v = ++version
    update({ loading: true, error: '', denied: false, notice: '' })
    try {
      const page = await service.load(more ? state.cursor : null, request.signal)
      if (!valid(v)) return
      const rows = more ? [...state.items, ...page.items] : page.items
      update({ items: rows.filter((row, i) => rows.findIndex(x => x.userId === row.userId) === i), cursor: page.nextCursor, hasNext: page.hasNext })
    } catch (error) { if (valid(v)) failure(error) }
    finally { if (valid(v)) update({ loading: false }) }
  }
  async function remove(userId: number) {
    if (!active || !current() || state.loading || state.removing !== null || !state.items.some(x => x.userId === userId)) return
    request.abort(); request = new AbortController(); const v = ++version
    update({ removing: userId, error: '', notice: '' })
    try {
      await service.remove(userId, request.signal)
      if (valid(v)) update({ items: state.items.filter(x => x.userId !== userId), notice: '차단을 해제했어요. 마감된 지원은 복원되지 않아요.' })
    } catch (error) { if (valid(v)) failure(error) }
    finally { if (valid(v)) update({ removing: null }) }
  }
  return { load, remove, dispose() { active = false; version++; request.abort() } }
}
