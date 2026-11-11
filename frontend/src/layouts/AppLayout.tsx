import { Link, NavLink, Outlet, useLocation } from 'react-router'
import '../features/posts/posts.css'

const links = [
  { to: '/login', label: '로그인' },
  { to: '/signup', label: '회원가입' },
  { to: '/school-verification', label: '학교 인증' },
  { to: '/profile/setup', label: '초기 프로필 설정' },
  { to: '/posts', label: '모집글 목록' },
  { to: '/posts/example-post', label: '모집글 상세' },
  { to: '/posts/new', label: '모집글 작성' },
  { to: '/posts/example-post/applicants', label: '모집글 지원자 관리' },
  { to: '/me/profile', label: '내 프로필' },
  { to: '/me/profile/edit', label: '프로필 수정' },
  { to: '/users/example-user', label: '다른 사용자 프로필' },
  { to: '/me/posts', label: '내 모집글' },
  { to: '/me/applications', label: '내 지원 내역' },
]

export function AppLayout() {
  const { pathname } = useLocation()
  const isRecruitment = pathname === '/posts' || /^\/posts\/[^/]+$/.test(pathname)
  if (isRecruitment) return (
    <div className="posts-layout">
      <a className="post-skip" href="#main-content">본문으로 건너뛰기</a>
      <header className="posts-header"><Link className="post-logo" to="/posts" aria-label="TEAM:ON 홈">TEAM<span>:ON</span></Link>
        <nav aria-label="주 메뉴"><NavLink to="/posts" end>홈</NavLink><NavLink to="/posts/new">모집글 작성</NavLink><button disabled title="알림 기능 준비 중">알림 · 준비 중</button><Link to="/me/profile">마이페이지</Link></nav>
      </header>
      <main id="main-content" className="posts-main"><Outlet /></main>
    </div>
  )
  return (
    <div className="app-layout">
      <header>
        <p className="app-title">천안·아산 대학생 팀원 모집</p>
        <nav aria-label="개발용 페이지 경로 확인">
          {links.map(({ to, label }) => <NavLink key={to} to={to} end>{label}</NavLink>)}
        </nav>
      </header>
      <main id="main-content"><Outlet /></main>
    </div>
  )
}
