import { useEffect, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { BrandLogo } from '../components/BrandLogo'
import { Input } from '../components/Input'
import { authErrors, readAccessToken } from '../features/auth/api'
import {
  confirmCode, getAccount, getVerification, listSchools, sendCode, submitDocument,
} from '../features/verification/api'
import type { Account, CodeDelivery, School, Verification } from '../features/verification/api'
import '../features/verification/verification.css'

const schoolDomains = (value: string) => value.split(',').map(d => d.trim().toLowerCase()).filter(Boolean)

type Challenge = CodeDelivery & { expiresAt: number }
type Action = 'send' | 'confirm' | 'document'
const steps = ['학교 선택', '학교 이메일', '코드 입력']
const maxFileSize = 5 * 1024 * 1024

function formatTime(seconds: number) {
  return [Math.floor(seconds / 60), seconds % 60].map(value => String(value).padStart(2, '0')).join(':')
}

function message(error: unknown) {
  return authErrors(error).message ?? (error instanceof Error ? error.message : '잠시 후 다시 시도해 주세요.')
}

export default function SchoolVerificationPage() {
  const token = readAccessToken()

  return (
    <div className="verification-page">
      <header className="verification-header">
        <Link to="/posts" aria-label="TEAM:ON 홈"><BrandLogo /></Link>
      </header>
      {token
        ? <VerificationForms token={token} />
        : <section className="verification-card verification-summary">
          <h1>학교 인증</h1>
          <p>학교 인증을 진행하려면 로그인해 주세요.</p>
          <Link to="/login">로그인 화면으로</Link>
        </section>}
    </div>
  )
}

function VerificationForms({ token }: { token: string }) {
  const navigate = useNavigate()
  const codeInput = useRef<HTMLInputElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const [schools, setSchools] = useState<School[]>([])
  const [account, setAccount] = useState<Account>()
  const [latest, setLatest] = useState<Verification | null>(null)
  const [schoolId, setSchoolId] = useState('')
  const [email, setEmail] = useState('')
  const [challenge, setChallenge] = useState<Challenge>()
  const [code, setCode] = useState('')
  const [seconds, setSeconds] = useState(0)
  const [file, setFile] = useState<File>()
  const [emailError, setEmailError] = useState('')
  const [codeError, setCodeError] = useState('')
  const [documentError, setDocumentError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [invalidSession, setInvalidSession] = useState(false)
  const [loading, setLoading] = useState(true)
  const [reload, setReload] = useState(0)
  const [action, setAction] = useState<Action>()
  const selectedSchool = schools.find(school => String(school.id) === schoolId)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      listSchools(controller.signal),
      getAccount(token, controller.signal),
      getVerification(token, controller.signal),
    ]).then(([schoolList, user, status]) => {
      if (controller.signal.aborted) return
      setSchools(schoolList)
      setAccount(user)
      setLatest(status)
      setSchoolId(current => current || (status ? String(status.school.id) : ''))
    }).catch(error => {
      if (controller.signal.aborted) return
      if (error instanceof ApiError && error.status === 401) setInvalidSession(true)
      else setLoadError(message(error))
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [reload, token])

  useEffect(() => {
    if (!challenge) return
    const timer = window.setInterval(() => {
      setSeconds(Math.max(0, Math.ceil((challenge.expiresAt - Date.now()) / 1000)))
    }, 250)
    return () => window.clearInterval(timer)
  }, [challenge])

  useEffect(() => {
    if (challenge?.expiresAt) codeInput.current?.focus()
  }, [challenge?.expiresAt])

  function refreshStatus() {
    setLoading(true)
    setLoadError('')
    setReload(value => value + 1)
  }

  function resetChallenge() {
    setChallenge(undefined)
    setCode('')
    setSeconds(0)
    setCodeError('')
  }

  function sessionExpired(error: unknown) {
    if (error instanceof ApiError && error.status === 401) {
      setInvalidSession(true)
      return true
    }
    return false
  }

  async function requestCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const schoolEmail = email.trim()
    if (!selectedSchool || action || (challenge && challenge.remainingSendCount <= 0)) return
    setEmailError('')
    // 메일 도메인이 두 종류인 학교는 쉼표로 이어져 와요 (예: vision.hoseo.edu,365.hoseo.edu)
    const domains = schoolDomains(selectedSchool.emailDomain)
    if (!domains.includes(schoolEmail.split('@').at(-1)?.toLowerCase() ?? '')) {
      setEmailError(`학교 이메일 도메인이 맞지 않아요. ${domains.map(d => `@${d}`).join(' 또는 ')} 주소를 입력해 주세요.`)
      return
    }
    setAction('send')
    try {
      const delivery = await sendCode(token, selectedSchool.id, schoolEmail)
      setSeconds(delivery.expiresInSeconds)
      setChallenge({ ...delivery, expiresAt: Date.now() + delivery.expiresInSeconds * 1000 })
      setCode('')
      setCodeError('')
    } catch (error) {
      if (sessionExpired(error)) return
      setEmailError(message(error))
      if (error instanceof ApiError && error.status === 429) {
        setChallenge(current => current && { ...current, remainingSendCount: 0 })
      }
    } finally {
      setAction(undefined)
    }
  }

  async function finishEmailVerification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (action || !challenge || !/^\d{6}$/.test(code)) return
    if (Date.now() >= challenge.expiresAt) {
      setSeconds(0)
      return
    }

    setAction('confirm')
    setCodeError('')
    try {
      const user = await confirmCode(token, code)
      setAccount(user)
      navigate(account?.reverifyDueDate ? '/me/profile' : '/profile/setup', { replace: true })
    } catch (error) {
      if (sessionExpired(error)) return
      if (error instanceof ApiError && error.code === 'VERIFICATION_CODE_EXPIRED') {
        setChallenge({ ...challenge, expiresAt: Date.now() })
        setSeconds(0)
      }
      setCodeError(message(error))
    } finally {
      setAction(undefined)
    }
  }

  function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const picked = event.currentTarget.files?.[0]
    setDocumentError('')
    if (picked && (!['image/jpeg', 'image/png'].includes(picked.type) || picked.size > maxFileSize || !picked.size)) {
      setDocumentError('JPG 또는 PNG 형식의 5MB 이하 사진을 선택해 주세요.')
      setFile(undefined)
      event.currentTarget.value = ''
      return
    }
    setFile(picked)
  }

  async function requestDocument(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedSchool || !file || action || latest?.status === 'PENDING') return
    setAction('document')
    setDocumentError('')
    try {
      const result = await submitDocument(token, selectedSchool.id, file)
      setLatest({ method: 'DOCUMENT', status: result.status, school: selectedSchool, rejectReason: null })
      setFile(undefined)
      if (fileInput.current) fileInput.current.value = ''
    } catch (error) {
      if (sessionExpired(error)) return
      setDocumentError(message(error))
      if (error instanceof ApiError && error.code === 'VERIFICATION_PENDING') refreshStatus()
    } finally {
      setAction(undefined)
    }
  }

  if (invalidSession) return <section className="verification-card verification-summary">
    <h1>학교 인증</h1>
    <p>로그인 시간이 만료됐어요. 다시 로그인해 주세요.</p>
    <Link to="/login">로그인 화면으로</Link>
  </section>

  if (!loading && !loadError && account?.status === 'VERIFIED' && !account.reverifyDueDate) {
    return <section className="verification-card verification-summary">
      <h1>학교 인증 완료</h1>
      <p>학교 인증이 완료됐어요.</p>
      <Link to="/profile/setup">초기 프로필 설정으로</Link>
    </section>
  }

  const blocked = loading || Boolean(loadError) || account?.status === 'SUSPENDED' || Boolean(action)
  const documentPending = latest?.method === 'DOCUMENT' && latest.status === 'PENDING'
  const documentRejected = latest?.method === 'DOCUMENT' && latest.status === 'REJECTED'
  const currentStep = challenge ? 2 : selectedSchool ? 1 : 0

  return (
    <div className="verification-grid">
      <section className="verification-card verification-email" aria-labelledby="school-verification-title">
        <h1 id="school-verification-title">학교 인증</h1>
        <p className="verification-help">인증 전에는 모집글 열람만 가능해요. 작성·지원·스크랩은 인증 후에 쓸 수 있어요.</p>
        {account?.reverifyDueDate && <p className="verification-status">이번 학기 재인증 기한은 {account.reverifyDueDate}까지예요.</p>}
        {account?.status === 'SUSPENDED' && <p className="verification-error" role="alert">이용 정지 중에는 학교 인증을 진행할 수 없어요.</p>}
        <ol className="verification-steps" aria-label="인증 단계">
          {steps.map((step, index) => <li key={step} data-reached={index <= currentStep}
            aria-current={index === currentStep ? 'step' : undefined}><span>{index + 1}</span>{step}</li>)}
        </ol>

        <form id="verification-email-form" onSubmit={requestCode} aria-busy={action === 'send'}>
          <label className="verification-label" htmlFor="verification-school">학교</label>
          <select id="verification-school" value={schoolId} disabled={blocked} required onChange={event => {
            setSchoolId(event.target.value)
            setEmailError('')
            setDocumentError('')
            resetChallenge()
          }}>
            <option value="">{loading ? '학교 목록을 불러오는 중…' : '학교를 선택해 주세요'}</option>
            {schools.map(school => <option key={school.id} value={school.id}>
              {[school.name, school.campus].filter(Boolean).join(' ')}
            </option>)}
          </select>
          {loadError && <p className="verification-error" role="alert">{loadError} <button type="button" className="verification-link" onClick={refreshStatus}>다시 시도</button></p>}
          {!loading && !loadError && !schools.length && <p className="verification-help">등록된 학교가 없어요. 팀 관리자에게 학교 등록을 요청해 주세요.</p>}
          <div className="verification-send">
            <Input id="verification-email" type="email" label="학교 이메일" required
              placeholder={selectedSchool ? schoolDomains(selectedSchool.emailDomain).map(d => `id@${d}`).join(' 또는 ') : '학교 이메일 입력'}
              value={email} disabled={blocked || !selectedSchool} onChange={event => {
                setEmail(event.target.value)
                setEmailError('')
                resetChallenge()
              }} />
            <Button type="submit" disabled={blocked || !selectedSchool || Boolean(challenge)}>
              {action === 'send' && !challenge ? '발송 중…' : '코드 받기'}
            </Button>
          </div>
          {emailError && <p className="verification-error" role="alert">{emailError}</p>}
        </form>

        <form className="verification-confirm" onSubmit={finishEmailVerification} aria-busy={action === 'confirm'}>
          <label className="verification-label" htmlFor="verification-code">인증 코드 6자리</label>
          <div className="verification-code-box">
            <input ref={codeInput} id="verification-code" type="text" inputMode="numeric" autoComplete="one-time-code"
              disabled={!challenge || seconds <= 0 || blocked} value={code} required pattern="[0-9]{6}"
              aria-invalid={Boolean(codeError)} onChange={event => {
                setCode(event.target.value.replace(/\D/g, '').slice(0, 6))
                setCodeError('')
              }} />
            <div className="verification-digits" aria-hidden="true">
              {Array.from({ length: 6 }, (_, index) => <span key={index} data-filled={Boolean(code[index])}>{code[index]}</span>)}
            </div>
          </div>
          {challenge && <div className="verification-meta">
            <span>{seconds > 0 ? `남은 시간 ${formatTime(seconds)}` : '코드가 만료됐어요. 다시 받아 주세요.'}</span>
            <button className="verification-link" type="submit" form="verification-email-form"
              disabled={blocked || challenge.remainingSendCount <= 0}>
              {action === 'send' ? '발송 중…' : `코드 재전송 (오늘 ${Math.max(0, challenge.remainingSendCount)}회 남음)`}
            </button>
          </div>}
          {codeError && <p className="verification-error" role="alert">{codeError}</p>}
          <Button type="submit" disabled={blocked || !challenge || seconds <= 0 || code.length !== 6}>
            {action === 'confirm' ? '확인 중…' : '인증 완료'}
          </Button>
        </form>
      </section>

      <section className="verification-card verification-document" aria-labelledby="document-title">
        <h2 id="document-title">학교 이메일이 없나요?</h2>
        <p className="verification-help">학생증 또는 재학증명서 사진으로 인증할 수 있어요.<br />관리자가 24시간 안에 확인해요.</p>
        <form onSubmit={requestDocument} aria-busy={action === 'document'}>
          <label className="verification-upload">
            <strong>{file?.name ?? '사진 올리기'}</strong>
            <span>JPG·PNG, 5MB 이하 · 승인 또는 반려 후 사진은 즉시 삭제돼요</span>
            <input ref={fileInput} type="file" accept="image/jpeg,image/png" onChange={chooseFile}
              disabled={blocked || documentPending} />
          </label>
          {documentPending && <p className="verification-status" role="status">승인 대기 중 · 24시간 이내에 결과를 알려드려요</p>}
          {documentRejected && <p className="verification-error" role="status">인증 반려 · {latest.rejectReason ?? '사진을 확인하고 다시 신청해 주세요.'}</p>}
          {documentError && <p className="verification-error" role="alert">{documentError}</p>}
          {documentPending
            ? <Button type="button" disabled={blocked} onClick={refreshStatus}>심사 상태 다시 확인</Button>
            : <Button type="submit" disabled={blocked || !selectedSchool || !file}>
              {action === 'document' ? '신청 중…' : '서류로 인증 신청'}
            </Button>}
        </form>
        <p className="verification-help">서류 인증에도 왼쪽에서 학교를 선택해 주세요.<br />학교 이메일 1개는 계정 1개에만 연결돼요.</p>
      </section>
    </div>
  )
}
