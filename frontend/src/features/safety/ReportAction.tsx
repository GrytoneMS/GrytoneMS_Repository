import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { Button } from '../../components/Button'
import { reportReasons } from '../profile/user-api'
import { useSafetyToken, safetyTokenCurrent } from './auth'
import { createReport, safetyError, type ReportReason, type ReportTarget } from './api'
import './safety.css'

export function ReportAction(props: { targetType: ReportTarget; targetId: number; label: string; disabled?: boolean }) {
  const token = useSafetyToken()
  return <ReportContent key={`${token}:${props.targetType}:${props.targetId}`} {...props} token={token} />
}
function ReportContent({ token, targetType, targetId, label, disabled }: { token: string | null; targetType: ReportTarget; targetId: number; label: string; disabled?: boolean }) {
  const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [reported, setReported] = useState(false), [message, setMessage] = useState('')
  const request = useRef<AbortController | null>(null)
  const lock = useRef(false)
  useEffect(() => () => request.current?.abort(), [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!token || !safetyTokenCurrent(token) || lock.current || reported) return
    const form = new FormData(event.currentTarget), reason = String(form.get('reason')) as ReportReason, detail = String(form.get('detail') ?? '')
    const controller = new AbortController(); request.current = controller; lock.current = true; setBusy(true); setMessage('')
    const current = () => !controller.signal.aborted && safetyTokenCurrent(token)
    try {
      await createReport(token, targetType, targetId, reason, detail, controller.signal)
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
    {open && (!token ? <p><Link to="/login">로그인</Link> 후 학교 인증을 완료하면 신고할 수 있어요.</p> : <form className="safety-report-form" onSubmit={submit} aria-label={label}>
      <strong>{label}</strong><p>학교 인증 회원만 신고할 수 있어요. 접수한 신고는 취소할 수 없어요.</p>
      <label>신고 사유<select name="reason" required disabled={busy} defaultValue=""><option value="">사유를 선택해 주세요</option>{Object.entries(reportReasons).map(([key, text]) => <option value={key} key={key}>{text}</option>)}</select></label>
      <label>상세 내용 (선택 · 200자 이하)<textarea name="detail" maxLength={200} disabled={busy} /></label>
      <div className="safety-actions"><Button className="safety-secondary" disabled={busy} onClick={() => setOpen(false)}>취소</Button><Button disabled={busy} type="submit">{busy ? '접수 중…' : '신고 접수'}</Button></div>
    </form>)}
  </div>
}
