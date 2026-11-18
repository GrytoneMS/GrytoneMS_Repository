import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../components/Button'
import { useAuth } from '../features/auth/useAuth'
import { AuthStatus } from '../features/auth/AuthContext'
import { notificationSettingsService, type SettingsService } from '../features/settings/api'
import { createSettingsController, initialSettings, settingsDirty } from '../features/settings/controller'
import '../features/settings/settings.css'

export default function NotificationSettingsPage() {
  const auth = useAuth()
  const service = useMemo(() => notificationSettingsService(auth.store, auth.session), [auth.store, auth.session])
  return <section className="settings-page" aria-labelledby="notification-settings-title">
    <Link className="settings-back" to="/settings">← 설정</Link><h1 id="notification-settings-title">알림 설정</h1>
    <p className="settings-lead">원하는 알림 환경을 선택하고 저장해 주세요.</p>
    {auth.status === 'authenticated' ? <SettingsForm key={auth.scope} service={service} /> : <AuthStatus />}
  </section>
}
function SettingsForm({ service }: { service: SettingsService }) {
  const [state, setState] = useState(initialSettings), controller = useRef<ReturnType<typeof createSettingsController> | null>(null)
  useEffect(() => { const value = createSettingsController(service, setState); controller.current = value; void value.load(); return () => value.dispose() }, [service])
  const saving = state.phase === 'saving'
  if (state.phase === 'idle' || state.phase === 'loading') return <p role="status" className="settings-card">알림 설정을 불러오는 중…</p>
  if (!state.draft) return <div className="settings-card"><p role="alert">{state.error}</p><Button onClick={() => void controller.current?.load()}>다시 불러오기</Button></div>
  return <>
    <aside className="settings-info"><strong>이 설정은 앱 푸시 알림에만 적용됩니다.</strong><p>현재 앱 푸시는 제공하지 않으며, 웹 알림함과 미읽음 개수에는 영향을 주지 않습니다.</p></aside>
    <form onSubmit={event => { event.preventDefault(); void controller.current?.save() }} className="settings-card">
      <fieldset disabled={saving} className="settings-fields"><legend>알림 환경</legend>
        {([
          ['applyEnabled', '지원 알림', '새 지원과 지원 결과에 대한 푸시 알림 설정이에요.'],
          ['deadlineEnabled', '마감 알림', '모집 마감에 대한 푸시 알림 설정이에요.'],
          ['quietHoursEnabled', '방해 금지 시간', '앱 푸시의 방해 금지 기능은 준비 중입니다.'],
        ] as const).map(([key, title, help]) => <label className="settings-toggle" key={key}><span><strong>{title}</strong><span id={`help-${key}`}>{help}</span></span><input type="checkbox" name={key} checked={state.draft![key]} aria-describedby={`help-${key}`} onChange={event => controller.current?.change(key, event.target.checked)} /></label>)}
      </fieldset>
      {state.error && <p className="settings-error" role="alert">{state.error}</p>}
      {state.notice && <p className="settings-success" role="status">{state.notice}</p>}
      <div className="settings-save"><p role="status">{saving ? '저장 중…' : settingsDirty(state) ? '아직 저장하지 않은 변경사항이 있어요.' : '변경사항이 없어요.'}</p><Button type="submit" disabled={saving || !settingsDirty(state)}>{saving ? '저장 중…' : '변경사항 저장'}</Button></div>
    </form>
  </>
}
