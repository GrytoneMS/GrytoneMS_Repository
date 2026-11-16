import { useContext, useSyncExternalStore } from 'react'
import { AuthContext } from './context'
export function useAuth() {
  const store = useContext(AuthContext), state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  return { ...state, store, scope: `${state.epoch}:${state.status}:${state.session?.userId ?? ''}` }
}
