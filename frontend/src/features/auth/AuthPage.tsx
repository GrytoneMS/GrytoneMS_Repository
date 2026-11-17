import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Button } from '../../components/Button'
import { Input } from '../../components/Input'
import { authErrors, login, saveAccessToken, signup } from './api'
import './auth.css'

type FormFeedback = ReturnType<typeof authErrors>

function LoginForm() {
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<FormFeedback>({})

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return

    const form = new FormData(event.currentTarget)
    setPending(true)
    setFeedback({})
    try {
      const { accessToken } = await login({
        email: String(form.get('email')).trim(),
        password: String(form.get('password')),
      })
      saveAccessToken(accessToken, form.has('remember'))
      navigate('/posts', { replace: true })
    } catch (error) {
      setFeedback(authErrors(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="auth-card auth-login" aria-labelledby="login-heading">
      <h1 id="login-heading">로그인</h1>
      <form onSubmit={submit} aria-busy={pending}>
        <Input id="login-email" name="email" type="email" autoComplete="username"
          label="이메일" placeholder="you@email.com" error={feedback.fields?.email} required />
        <Input name="password" type="password" autoComplete="current-password"
          label="비밀번호" placeholder="비밀번호 입력" error={feedback.fields?.password} required />
        {feedback.message && <p className="auth-alert" role="alert">{feedback.message}</p>}
        <label className="auth-check"><input type="checkbox" name="remember" defaultChecked /> 자동 로그인 유지</label>
        <Button type="submit" disabled={pending}>{pending ? '로그인 중…' : '로그인'}</Button>
      </form>
      <div className="auth-links">
        <Link to="/forgot-password">비밀번호 재설정</Link>
        <Link to="/signup">이메일로 회원가입</Link>
      </div>
    </section>
  )
}

function SignupForm() {
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<FormFeedback>({})

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return

    const form = new FormData(event.currentTarget)
    setPending(true)
    setFeedback({})
    try {
      const { accessToken } = await signup({
        email: String(form.get('email')).trim(),
        password: String(form.get('password')),
        nickname: String(form.get('nickname')),
        agreeTerms: form.has('agreeTerms'),
        agreePrivacy: form.has('agreePrivacy'),
      })
      saveAccessToken(accessToken, false)
      navigate('/school-verification', { replace: true })
    } catch (error) {
      setFeedback(authErrors(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <section className="auth-card auth-signup" aria-labelledby="signup-heading">
      <h1 id="signup-heading">회원가입</h1>
      <form onSubmit={submit} aria-busy={pending}>
        <Input id="signup-email" name="email" type="email" autoComplete="email"
          label="이메일" placeholder="you@email.com" error={feedback.fields?.email} required />
        <Input name="password" type="password" autoComplete="new-password"
          label="비밀번호 (8자 이상, 영문+숫자)" placeholder="비밀번호 입력"
          minLength={8} pattern="(?=.*[A-Za-z])(?=.*[0-9]).{8,}" title="영문과 숫자를 포함해 8자 이상 입력해 주세요."
          error={feedback.fields?.password} required />
        <Input name="nickname" autoComplete="nickname" label="닉네임" placeholder="닉네임 입력"
          minLength={2} maxLength={12} pattern="[가-힣A-Za-z0-9]{2,12}"
          title="한글·영문·숫자로 2~12자 입력해 주세요." error={feedback.fields?.nickname} required />
        <label className="auth-check"><input type="checkbox" name="agreeTerms" required /> 이용약관 동의 (필수)</label>
        {feedback.fields?.agreeTerms && <p className="field-error" role="alert">{feedback.fields.agreeTerms}</p>}
        <label className="auth-check"><input type="checkbox" name="agreePrivacy" required /> 개인정보 처리방침 동의 (필수)</label>
        {feedback.fields?.agreePrivacy && <p className="field-error" role="alert">{feedback.fields.agreePrivacy}</p>}
        {feedback.message && <p className="auth-alert" role="alert">{feedback.message}</p>}
        <Button type="submit" disabled={pending}>{pending ? '가입 중…' : '가입하기'}</Button>
      </form>
      <div className="auth-links auth-signup-links"><Link to="/login">로그인으로 돌아가기</Link></div>
    </section>
  )
}

export function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const page = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (mode === 'signup') page.current?.querySelector<HTMLInputElement>('#signup-email')?.focus()
  }, [mode])

  return (
    <div ref={page} className={`auth-page auth-page--${mode}`}>
      <Link className="auth-logo" to="/posts" aria-label="TEAM:ON 홈">TEAM<span>:ON</span></Link>
      <div className="auth-grid">
        <aside className="auth-intro" aria-label="서비스 소개">
          <p className="auth-intro-title">학교를 넘어<br />팀을 만나요</p>
          <p>천안·아산 9개 대학 학생이 역할 단위로 지원하고, 팀장이 선택해요. 연락 수단은 수락된 사람에게만 공개돼요.</p>
          <ul><li>학교 인증</li><li>역할별 지원</li><li>수락 후 연락 공개</li></ul>
        </aside>
        <LoginForm />
        <SignupForm />
      </div>
    </div>
  )
}
