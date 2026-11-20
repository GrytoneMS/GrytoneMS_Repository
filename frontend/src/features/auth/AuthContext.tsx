import { Fragment, useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router'
import { Button } from '../../components/Button'
import { clearAccessToken, readAccessToken } from './api'
import { createAuthStore, type AuthState } from './session'
import { AuthContext } from './context'
import { useAuth } from './useAuth'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [store] = useState(() => createAuthStore(() => typeof window === 'undefined' ? null : readAccessToken()))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const { pathname } = useLocation(), previous = useRef(pathname)
  useLayoutEffect(() => {
    store.resume(); void store.refresh()
    const sync = () => { void store.sync() }, focus = () => { void store.refresh() }
    window.addEventListener('storage', sync); window.addEventListener('teamon:auth-changed', sync); window.addEventListener('focus', focus)
    const timer = window.setInterval(sync, 400)
    return () => { store.dispose(); clearInterval(timer); window.removeEventListener('storage', sync); window.removeEventListener('teamon:auth-changed', sync); window.removeEventListener('focus', focus) }
  }, [store])
  useEffect(() => { if (previous.current !== pathname && (previous.current === '/school-verification' || previous.current === '/profile/setup')) void store.refresh(); previous.current = pathname }, [pathname, store])
  return <AuthContext.Provider value={store}><Fragment key={state.epoch}>{children}</Fragment></AuthContext.Provider>
}
export function AuthStatus({ state: supplied, publicView = false }: { state?: AuthState; publicView?: boolean } = {}) {
  const auth = useAuth(), state = supplied ?? auth
  return <aside className="post-note auth-status" aria-label="로그인 상태" role="status">
    <span>{state.status === 'checking' ? '로그인 상태를 확인 중이에요.' : state.status === 'error' ? state.message : state.status === 'anonymous' ? state.message || (publicView ? '비로그인 · 공개 모집글을 조회할 수 있어요.' : '로그인 후 이용할 수 있어요.') : <>{state.session?.account.nickname ?? '회원'} · {state.session?.account.status === 'VERIFIED' ? '학교 인증 완료' : state.session?.account.status === 'SUSPENDED' ? '이용 정지' : '학교 미인증'}</>}</span>
    {state.status === 'anonymous' && <> <Link to="/login">로그인</Link></>}
    {state.status === 'authenticated' && state.session?.account.status === 'UNVERIFIED' && <> <Link to="/school-verification">학교 인증</Link></>}
    {state.status === 'error' && <Button onClick={() => void auth.store.refresh()}>로그인 상태 다시 조회</Button>}
    {state.status !== 'checking' && state.status !== 'anonymous' && <Button className="auth-status-logout" onClick={clearAccessToken}>로그아웃</Button>}
  </aside>
}
