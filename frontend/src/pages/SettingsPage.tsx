import { Link } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { AuthStatus } from '../features/auth/AuthContext'
import '../features/settings/settings.css'

export default function SettingsPage() {
  const auth = useAuth()
  return <section className="settings-page" aria-labelledby="settings-title">
    <Link className="settings-back" to="/me/profile">← 마이페이지</Link>
    <h1 id="settings-title">설정</h1><p className="settings-lead">알림과 서비스 이용 환경을 관리하세요.</p>
    {auth.status !== 'authenticated' ? <AuthStatus /> : <>
      <section className="settings-card" aria-labelledby="settings-preferences"><h2 id="settings-preferences">서비스 설정</h2>
        <Link className="settings-menu" to="/settings/notifications"><span><strong>알림 설정</strong><span>지원·마감 알림과 방해 금지 설정</span></span><span aria-hidden="true">›</span></Link>
        <Link className="settings-menu" to="/me/blocks"><span><strong>차단 사용자 관리</strong><span>차단한 사용자 목록 관리</span></span><span aria-hidden="true">›</span></Link>
      </section>
      <section className="settings-card" aria-labelledby="settings-account"><h2 id="settings-account">계정 관리</h2>
        <Link className="settings-menu" to="/forgot-password"><strong>비밀번호 재설정</strong><span aria-hidden="true">›</span></Link>
        <Link className="settings-menu" to="/settings/withdraw"><strong>회원 탈퇴</strong><span aria-hidden="true">›</span></Link>
      </section>
    </>}
  </section>
}
