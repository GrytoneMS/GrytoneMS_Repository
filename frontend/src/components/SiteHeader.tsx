import { Link, NavLink, useLocation } from 'react-router'
import { NotificationLink } from '../features/notifications/NotificationLink'
import { BrandLogo } from './BrandLogo'
import './site-header.css'

function HeaderLabel({ children }: { children: string }) {
  return <span className="header-label" data-label={children}><span>{children}</span></span>
}

export function SiteHeader() {
  const { pathname } = useLocation()
  const myPage = pathname === '/me/profile' || pathname === '/me/profile/edit' || pathname === '/me/posts'
  return <header className="posts-header">
    <Link className="post-logo" to="/posts" aria-label="TEAM:ON 홈"><BrandLogo /></Link>
    <nav aria-label="주 메뉴">
      <NavLink to="/posts" end><HeaderLabel>홈</HeaderLabel></NavLink>
      <NavLink to="/posts/new"><HeaderLabel>모집글 작성</HeaderLabel></NavLink>
      <NotificationLink />
      <Link to="/me/profile" aria-current={myPage ? 'page' : undefined}><HeaderLabel>마이페이지</HeaderLabel></Link>
    </nav>
  </header>
}
