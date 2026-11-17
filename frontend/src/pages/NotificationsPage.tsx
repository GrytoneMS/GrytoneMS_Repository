import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { AuthStatus } from '../features/auth/AuthContext'
import { authErrors } from '../features/auth/api'
import { notificationDestination, notificationLabel } from '../features/notifications/api'
import type { Notification, NotificationPage } from '../features/notifications/api'
import { useNotifications } from '../features/notifications/context'

export default function NotificationsPage() {
  const { available, example } = useNotifications()
  return <section className="notifications-page" aria-labelledby="notifications-title">
    <div className="notifications-heading">
      <h1 id="notifications-title">알림함</h1>
      {example && <span>예시 데이터</span>}
    </div>
    {available ? <NotificationList key={String(example)} /> : <AuthStatus />}
  </section>
}

function NotificationList() {
  const { api, unread, refreshCount } = useNotifications()
  const navigate = useNavigate()
  const [page, setPage] = useState<NotificationPage>()
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [pending, setPending] = useState(false)
  const operation = useRef<AbortController | undefined>(undefined)

  useEffect(() => {
    const controller = new AbortController()
    api.list(null, controller.signal).then(result => {
      if (!controller.signal.aborted) setPage(result)
    }).catch(reason => {
      if (!controller.signal.aborted) setError(authErrors(reason).message ?? '알림을 불러오지 못했어요.')
    })
    return () => { controller.abort(); operation.current?.abort() }
  }, [api, attempt])

  async function run(task: (signal: AbortSignal) => Promise<void>) {
    if (operation.current) return
    const controller = new AbortController()
    operation.current = controller
    setPending(true)
    setError('')
    try {
      await task(controller.signal)
    } catch (reason) {
      if (!controller.signal.aborted) setError(authErrors(reason).message ?? '알림을 처리하지 못했어요.')
    } finally {
      operation.current = undefined
      if (!controller.signal.aborted) setPending(false)
    }
  }

  function open(item: Notification) {
    const destination = notificationDestination(item)
    void run(async signal => {
      if (!item.read) {
        await api.read(item.id, signal)
        if (signal.aborted) return
        setPage(current => current && ({ ...current, items: current.items.map(entry => entry.id === item.id ? { ...entry, read: true } : entry) }))
        refreshCount()
      }
      if (destination) navigate(destination)
    })
  }

  function readAll() {
    void run(async signal => {
      await api.readAll(signal)
      if (signal.aborted) return
      setPage(current => current && ({ ...current, items: current.items.map(item => ({ ...item, read: true })) }))
      refreshCount()
    })
  }

  function loadMore() {
    if (!page?.nextCursor) return
    const cursor = page.nextCursor
    void run(async signal => {
      const next = await api.list(cursor, signal)
      if (signal.aborted) return
      setPage(current => {
        if (!current) return next
        const known = new Set(current.items.map(item => item.id))
        return { ...next, items: [...current.items, ...next.items.filter(item => !known.has(item.id))] }
      })
    })
  }

  return <>
    <div className="notifications-toolbar">
      <span>{unread !== null && `안 읽음 ${unread} · `}최근 30일 보관</span>
      {page && <button type="button" disabled={pending || (unread ?? Number(page.items.some(item => !item.read))) === 0}
        onClick={readAll}>모두 읽음</button>}
    </div>
    <div className="notifications-panel" aria-busy={pending}>
      {error && <p role="alert">{error}</p>}
      {!page && (error
        ? <button type="button" onClick={() => { setError(''); setAttempt(value => value + 1); refreshCount() }}>다시 시도</button>
        : <p role="status">알림을 불러오는 중이에요…</p>)}
      {page && (page.items.length === 0 ? <p>아직 받은 알림이 없어요.</p> : <ul>
        {page.items.map(item => {
          const canOpen = !item.read || Boolean(notificationDestination(item))
          const content = <><span className="notification-dot" aria-hidden="true" /><span>
            <span className="notification-label">{notificationLabel(item.type)}{!item.read && <span className="notification-visually-hidden"> · 읽지 않음</span>}</span>
            <span className="notification-message">{item.message}</span>
          </span></>
          return <li key={item.id}>
            {canOpen
              ? <button type="button" className="notification-row" data-unread={!item.read} disabled={pending} onClick={() => open(item)}>{content}</button>
              : <div className="notification-row">{content}</div>}
          </li>
        })}
      </ul>)}
      {page?.hasNext && page.nextCursor && <button className="notification-more" type="button" disabled={pending} onClick={loadMore}>더 보기</button>}
    </div>
  </>
}
