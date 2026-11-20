import { ApiError, apiRequest } from '../../api/client'
import type { AuthStore } from '../auth/session'

export type NotificationSettings = { applyEnabled: boolean; deadlineEnabled: boolean; quietHoursEnabled: boolean }
export const settingKeys = ['applyEnabled', 'deadlineEnabled', 'quietHoursEnabled'] as const
function checked(value: NotificationSettings): NotificationSettings {
  if (!value || settingKeys.some(key => typeof value[key] !== 'boolean')) throw new ApiError(200, { success: false, code: 'INVALID_RESPONSE', message: '알림 설정 응답을 확인하지 못했어요. 다시 시도해 주세요.' })
  return { applyEnabled: value.applyEnabled, deadlineEnabled: value.deadlineEnabled, quietHoursEnabled: value.quietHoursEnabled }
}
export function notificationSettingsService(store: AuthStore, session = store.getSnapshot().session) {
  const current = () => !!session && store.getSnapshot().status === 'authenticated' && store.getSnapshot().session === session
  const request = (signal: AbortSignal, body?: NotificationSettings) => store.execute(async (active, authSignal) => {
    if (active !== session) throw new DOMException('계정이 변경됐습니다.', 'AbortError')
    return checked(await apiRequest<NotificationSettings>('/api/users/me/notification-settings', {
      token: active.token, signal: AbortSignal.any([signal, authSignal]), cache: 'no-store',
      ...(body ? { method: 'PUT', body: checked(body) } : {}),
    }))
  })
  return { current, load: (signal: AbortSignal) => request(signal), save: (body: NotificationSettings, signal: AbortSignal) => request(signal, body) }
}
export type SettingsService = ReturnType<typeof notificationSettingsService>
