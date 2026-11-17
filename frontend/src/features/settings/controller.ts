import { ApiError } from '../../api/client'
import { settingKeys, type NotificationSettings, type SettingsService } from './api'

export type SettingsState = { phase: 'idle' | 'loading' | 'load-error' | 'ready' | 'saving'; saved: NotificationSettings | null; draft: NotificationSettings | null; error: string; notice: string }
export const initialSettings: SettingsState = { phase: 'idle', saved: null, draft: null, error: '', notice: '' }
export const settingsDirty = (state: SettingsState) => !!state.draft && !!state.saved && settingKeys.some(key => state.draft![key] !== state.saved![key])
function feedback(error: unknown, saving: boolean) {
  if (error instanceof ApiError) {
    if (error.status === 401) return '로그인이 만료됐어요. 다시 로그인해 주세요.'
    if (error.status === 403) return '알림 설정을 변경할 권한이 없어요.'
  }
  return saving ? '저장 결과를 확인하지 못했어요. 변경한 값은 유지됩니다. 다시 저장하거나 나중에 확인해 주세요.' : '알림 설정을 불러오지 못했어요. 다시 시도해 주세요.'
}
export function createSettingsController(service: SettingsService, publish: (state: SettingsState) => void) {
  let state = { ...initialSettings }, active = true, version = 0, request = new AbortController()
  const update = (patch: Partial<SettingsState>) => { state = { ...state, ...patch }; publish(state) }
  const current = (v: number) => active && service.current() && version === v && !request.signal.aborted
  async function load() {
    if (!active || !service.current() || state.phase === 'loading' || state.phase === 'saving' || state.saved) return
    request.abort(); request = new AbortController(); const v = ++version
    update({ phase: 'loading', error: '', notice: '' })
    try {
      const value = await service.load(request.signal)
      if (current(v)) update({ phase: 'ready', saved: { ...value }, draft: { ...value } })
    } catch (error) { if (current(v)) update({ phase: 'load-error', error: feedback(error, false) }) }
  }
  function change(key: keyof NotificationSettings, enabled: boolean) {
    if (!active || !service.current() || state.phase !== 'ready' || !state.draft || !settingKeys.includes(key)) return
    update({ draft: { ...state.draft, [key]: enabled }, error: '', notice: '' })
  }
  async function save() {
    if (!active || !service.current() || state.phase !== 'ready' || !state.draft || !settingsDirty(state)) return
    request.abort(); request = new AbortController(); const v = ++version, body = { ...state.draft }
    update({ phase: 'saving', error: '', notice: '' })
    try {
      const value = await service.save(body, request.signal)
      if (current(v)) update({ phase: 'ready', saved: { ...value }, draft: { ...value }, notice: '알림 설정을 저장했어요.' })
    } catch (error) { if (current(v)) update({ phase: 'ready', error: feedback(error, true) }) }
  }
  return { load, change, save, dispose() { active = false; version++; request.abort() } }
}
