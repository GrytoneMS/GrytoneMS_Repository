import type { AuthStore } from '../auth/session'
import { realAdmin, type AdminService } from './api'

export function authenticatedAdmin(store: AuthStore, session = store.getSnapshot().session): AdminService {
  const base = realAdmin(session?.token ?? null, () => store.getSnapshot().session === session,
    () => store.execute(async current => current.account))
  return {
    account: signal => store.execute((_, authSignal) => base.account(AbortSignal.any([signal, authSignal]))),
    list: (menu, status, cursor, signal) => store.execute((_, authSignal) => base.list(menu, status, cursor, AbortSignal.any([signal, authSignal]))),
    submit: (job, signal) => store.execute((_, authSignal) => base.submit(job, AbortSignal.any([signal, authSignal]))),
    document: (id, signal) => store.execute((_, authSignal) => base.document(id, AbortSignal.any([signal, authSignal]))),
  }
}
