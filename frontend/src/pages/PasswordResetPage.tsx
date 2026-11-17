import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { createResetController, initialReset, requestNotice } from '../features/auth/password-reset'
import '../features/auth/auth.css'
import '../features/auth/password-reset.css'

export default function PasswordResetPage({ confirm = false }: { confirm?: boolean }) {
  const location = useLocation()
  return <ResetForm key={`${location.key}:${confirm}`} confirm={confirm} search={location.search} />
}
function ResetForm({ confirm, search }: { confirm: boolean; search: string }) {
  const [token, setToken] = useState(() => new URLSearchParams(search).get('token')?.trim() || '')
  const [state, setState] = useState(initialReset)
  const form = useRef<HTMLFormElement>(null)
  const controller = useRef<ReturnType<typeof createResetController> | null>(null)
  useEffect(() => {
    // Do not retain the secret in browser history or outgoing referrers.
    if (confirm) window.history.replaceState(window.history.state, '', window.location.pathname)
    const current = createResetController(value => {
      if (value.done) { form.current?.reset(); setToken('') }
      setState(value)
    })
    controller.current = current
    return () => current.dispose()
  }, [confirm])
  const missing = confirm && !token && !state.done
  return <div className="auth-page auth-page--reset">
    <Link className="auth-logo" to="/posts" aria-label="TEAM:ON 홈">TEAM<span>:ON</span></Link>
    <section className="auth-card reset-card" aria-labelledby="reset-heading">
      <h1 id="reset-heading">{confirm ? '새 비밀번호 설정' : '비밀번호 재설정'}</h1>
      {state.done ? <div role="status"><p>{confirm ? '비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.' : requestNotice}</p></div> : missing ? <p className="auth-alert" role="alert">링크가 없거나 올바르지 않아요. 재설정 메일을 다시 요청해 주세요.</p> : <>
        <p className="reset-description">{confirm ? '영문과 숫자를 포함해 8~64자로 입력해 주세요. 메일 링크는 30분 동안 한 번만 사용할 수 있어요.' : '가입할 때 사용한 이메일을 입력해 주세요.'}</p>
        <form ref={form} noValidate aria-busy={state.pending} onSubmit={event => {
          event.preventDefault()
          const data = new FormData(event.currentTarget)
          void controller.current?.submit({ email: String(data.get('email') ?? ''), ...(confirm ? { token, password: String(data.get('newPassword') ?? ''), confirmation: String(data.get('confirmation') ?? '') } : {}) })
        }}>
          <fieldset disabled={state.pending || state.invalid} className="reset-fields">
            {confirm ? <><Input name="newPassword" label="새 비밀번호" type="password" autoComplete="new-password" minLength={8} maxLength={64} required /><Input name="confirmation" label="새 비밀번호 확인" type="password" autoComplete="new-password" maxLength={64} required /></> : <Input name="email" label="이메일" type="email" autoComplete="email" required />}
            <Button type="submit" disabled={state.pending || state.invalid}>{state.pending ? '처리 중…' : confirm ? '새 비밀번호 저장' : '재설정 메일 요청'}</Button>
          </fieldset>
          {state.error && <p className="auth-alert" role="alert">{state.error}</p>}
        </form>
      </>}
      <div className="auth-links"><Link to="/login">로그인으로 돌아가기</Link>{(confirm || state.done) && <Link to="/forgot-password" reloadDocument>재설정 메일 다시 요청</Link>}</div>
    </section>
  </div>
}
