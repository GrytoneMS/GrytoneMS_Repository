import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { useAuth } from '../auth/useAuth'
import { notificationApi, notificationExample } from './api'
import { NotificationContext } from './context'

export function NotificationProvider({ children }: { children: ReactNode }) {
  const { pathname, search } = useLocation()
  const auth = useAuth()
  const example = import.meta.env.DEV && pathname === '/notifications'
    && new URLSearchParams(search).get('notificationExample') === 'true'
  const available = example || auth.status === 'authenticated'
  const api = useMemo(() => example ? notificationExample() : notificationApi(auth.store), [auth.store, example])
  const [count, setCount] = useState<{ api: typeof api; scope: string; value: number | null }>()
  const [revision, setRevision] = useState(0)
  const refreshCount = useCallback(() => setRevision(value => value + 1), [])
  const scope = example ? 'example' : auth.scope

  useEffect(() => {
    if (!available) return
    const controller = new AbortController()
    api.count(controller.signal).then(value => {
      if (!controller.signal.aborted) setCount({ api, scope, value })
    }).catch(() => {
      if (!controller.signal.aborted) setCount({ api, scope, value: null })
    })
    return () => controller.abort()
  }, [api, available, pathname, revision, scope])

  const unread = available && count?.api === api && count.scope === scope ? count.value : null
  return <NotificationContext.Provider value={{ api, example, available, unread, refreshCount }}>
    {children}
  </NotificationContext.Provider>
}
