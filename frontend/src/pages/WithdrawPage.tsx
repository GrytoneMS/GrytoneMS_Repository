import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { AuthStatus } from '../features/auth/AuthContext'
import { clearAccessToken } from '../features/auth/api'
import { useAuth } from '../features/auth/useAuth'
import { withdrawalService, type WithdrawalService } from '../features/withdrawal/api'
import { createWithdrawalController, initialWithdrawal } from '../features/withdrawal/controller'
import '../features/auth/auth.css'
import '../features/withdrawal/withdrawal.css'

export default function WithdrawPage() {
  const auth = useAuth(), location = useLocation(), navigate = useNavigate()
  const service = useMemo(() => withdrawalService(auth.store, auth.session), [auth.store, auth.session])
  const done = location.state?.withdrawalCompleted === true
  return <div className="auth-page"><Link className="post-logo withdraw-logo" to="/posts" aria-label="TEAM:ON 홈"><img src="/brand/team-on.png" alt="TEAM:ON" width={659} height={115} /></Link>
    <section className="auth-card withdraw-card" aria-labelledby="withdraw-title"><h1 id="withdraw-title">회원 탈퇴</h1>
      {done ? <><p role="status">회원 탈퇴가 완료됐어요. 이용해 주셔서 감사합니다.</p><div className="auth-links"><Link to="/posts">홈으로</Link><Link to="/login">로그인</Link></div></> : auth.status !== 'authenticated' ? <><AuthStatus /><p>로그인이 만료되었거나 결과 확인 중 세션이 종료됐다면 다시 로그인해 계정 상태를 확인해 주세요. 로그인 불가만으로 탈퇴 완료를 단정할 수 없습니다.</p></> : <WithdrawForm key={auth.scope} service={service} complete={() => {
        navigate('/settings/withdraw', { replace: true, state: { withdrawalCompleted: true } })
        clearAccessToken(); void auth.store.sync()
      }} />}
    </section>
  </div>
}
function WithdrawForm({ service, complete }: { service: WithdrawalService; complete: () => void }) {
  const [state, setState] = useState(initialWithdrawal), [confirming, setConfirming] = useState(false)
  const controller = useRef<ReturnType<typeof createWithdrawalController> | null>(null), form = useRef<HTMLFormElement>(null)
  const completeRef = useRef(complete)
  useEffect(() => { completeRef.current = complete }, [complete])
  useEffect(() => {
    const current = createWithdrawalController(service, setState, () => { form.current?.reset(); completeRef.current() })
    controller.current = current; void current.load(); return () => current.dispose()
  }, [service])
  const uncertain = state.phase === 'uncertain' || state.phase === 'checking'
  const pending = state.phase === 'sending'
  return <>
    <ul className="withdraw-policy">
      <li>공개된 모집 중인 내 글은 최신 100개까지 마감 처리하고 대기 지원자에게 마감 알림을 보냅니다. 내가 보낸 대기 지원은 취소됩니다.</li>
      <li>이메일·학교 이메일·학과·학년·소개·프로필 이미지를 비우고 닉네임을 탈퇴 표시로 변경합니다. 역할·기술·프로필 항목과 심사 대기 서류 인증을 정리합니다.</li>
      <li>모든 데이터가 삭제되지는 않습니다. 수락된 참여 기록은 자동 취소되지 않으며, 모집글·지원 이력은 남을 수 있습니다.</li>
      <li>모집글·참여 팀이 있어도 탈퇴할 수 있습니다. 탈퇴 후 현재 계정으로 서비스를 이용할 수 없습니다.</li>
    </ul>
    {state.phase === 'loading' && <p role="status">탈퇴 전 정보를 조회하는 중…</p>}
    {state.counts && <dl className="withdraw-counts"><div><dt>모집 중인 글</dt><dd>{state.counts.openPostCount}개</dd></div><div><dt>수락되어 참여 중인 팀</dt><dd>{state.counts.acceptedTeamCount}개</dd></div></dl>}
    {state.error && <p className="auth-alert" role="alert">{state.error}</p>}
    {state.phase === 'load-error' && <Button onClick={() => void controller.current?.load()}>다시 조회</Button>}
    {uncertain ? <div className="auth-links"><Button disabled={state.phase === 'checking'} onClick={() => void controller.current?.checkSession()}>로그인 상태 조회</Button><Link to="/login">로그인 화면</Link></div> : (state.phase === 'ready' || pending) && <form ref={form} noValidate onSubmit={event => {
      event.preventDefault(); const data = new FormData(event.currentTarget)
      void controller.current?.submit(String(data.get('password') ?? ''), confirming && data.has('confirm'))
    }}>
      <fieldset className="withdraw-fields" disabled={pending}>
        <Input name="password" label="현재 비밀번호" type="password" autoComplete="current-password" required />
        {!confirming ? <Button onClick={() => setConfirming(true)}>탈퇴 내용 최종 확인</Button> : <div className="withdraw-final"><p>이 계정을 탈퇴하시겠어요? 아래 확인 후 탈퇴 버튼을 눌러 주세요.</p><label className="auth-check"><input name="confirm" type="checkbox" required /> 안내를 확인했으며 회원 탈퇴에 동의합니다.</label><Button type="submit" disabled={pending}>{pending ? '탈퇴 요청 중…' : '확인했고 탈퇴합니다'}</Button><Button onClick={() => { setConfirming(false); form.current?.reset() }}>취소하고 다시 검토</Button></div>}
      </fieldset>
    </form>}
    {!pending && <div className="auth-links"><Link to="/me/profile">마이페이지로 돌아가기</Link></div>}
  </>
}
