import { createContext, useContext } from 'react'
import type { notificationApi } from './api'

export const NotificationContext = createContext<{
  api: ReturnType<typeof notificationApi>
  example: boolean
  available: boolean
  unread: number | null
  refreshCount: () => void
} | null>(null)

export function useNotifications() {
  const value = useContext(NotificationContext)
  if (!value) throw new Error('알림 제공자가 필요해요.')
  return value
}
