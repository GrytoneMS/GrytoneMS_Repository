import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { Button } from '../../components/Button'
import { reportReasons } from '../profile/user-api'
import { useAuth } from '../auth/useAuth'
import { AuthStatus } from '../auth/AuthContext'
import { authenticatedSafety, type SafetyService } from './source'
import { safetyError, type ReportReason, type ReportTarget } from './api'
import './safety.css'

export function ReportAction(props: { targetType: ReportTarget; targetId: number; label: string; disabled?: boolean }) {
  const auth = useAuth()
  const service = useMemo(() => authenticatedSafety(auth.store, auth.session), [auth.store, auth.session])
  return <ReportContent key={`${auth.scope}:${props.targetType}:${props.targetId}`} {...props} service={service} ready={auth.status === 'authenticated'} />
}
function ReportContent({ service, ready, targetType, targetId, label, disabled }: { service: SafetyService; ready: boolean; targetType: ReportTarget; targetId: number; label: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [reported, setReported] = useState(false), [message, setMessage] = useState('')
  const request = useRef<AbortController | null>(null)
  const lock = useRef(false)
  useEffect(() => () => request.current?.abort(), [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!ready || !service.current() || disabled || lock.current || reported) return
    const form = new FormData(event.currentTarget), reason = String(form.get('reason')) as ReportReason, detail = String(form.get('detail') ?? '')
    const controller = new AbortController(); request.current = controller; lock.current = true; setBusy(true); setMessage('')
    const current = () => !controller.signal.aborted && service.current()
    try {
      await service.report(targetType, targetId, reason, detail, controller.signal)
      if (current()) { setReported(true); setMessage('신고가 접수됐어요.'); setOpen(false) }
    } catch (error) {
      if (current()) {
        setMessage(safetyError(error))
        if (error instanceof ApiError && error.code === 'ALREADY_REPORTED') { setReported(true); setOpen(false) }
      }
    } finally { lock.current = false; if (current()) setBusy(false) }
  }
  return <div className="safety-report">
    <Button className="safety-secondary" disabled={disabled || busy || reported} onClick={() => { setOpen(value => !value); setMessage('') }}>{reported ? '신고 접수됨' : label}</Button>
    {message && <p role={reported ? 'status' : 'alert'}>{message}</p>}
    {open && (!ready ? <AuthStatus /> : <form className="safety-report-form" onSubmit={submit} aria-label={label}>
      <strong>{label}</strong><p>학교 인증 회원만 신고할 수 있어요. 접수한 신고는 취소할 수 없어요.</p>
      <label>신고 사유<select name="reason" required disabled={busy} defaultValue=""><option value="">사유를 선택해 주세요</option>{Object.entries(reportReasons).map(([key, text]) => <option value={key} key={key}>{text}</option>)}</select></label>
      <label>상세 내용 (선택 · 200자 이하)<textarea name="detail" maxLength={200} disabled={busy} /></label>
      <div className="safety-actions"><Button className="safety-secondary" disabled={busy} onClick={() => setOpen(false)}>취소</Button><Button disabled={busy} type="submit">{busy ? '접수 중…' : '신고 접수'}</Button></div>
    </form>)}
  </div>
}
