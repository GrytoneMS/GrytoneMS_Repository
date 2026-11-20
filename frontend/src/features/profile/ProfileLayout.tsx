import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { Button } from '../../components/Button'
import { SiteHeader } from '../../components/SiteHeader'
import { readAccessToken } from '../auth/api'
import { getMyProfile } from './api'
import type { Profile } from './api'
import { profileError } from './presentation'
import './profile.css'

export function ProfileLayout({ children }: { children: ReactNode }) {
  return (
    <div className="profile-page">
      <SiteHeader />
      <div className="profile-content">{children}</div>
    </div>
  )
}

export function MyProfileData({ children }: { children: (profile: Profile, token: string) => ReactNode }) {
  const token = readAccessToken()
  const [profile, setProfile] = useState<Profile>()
  const [error, setError] = useState<unknown>()
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    getMyProfile(token, controller.signal).then(result => {
      if (!controller.signal.aborted) setProfile(result)
    }).catch(reason => {
      if (!controller.signal.aborted) setError(reason)
    })
    return () => controller.abort()
  }, [token, attempt])

  return (
    <ProfileLayout>
      {!token || (error instanceof ApiError && error.status === 401)
        ? <section className="profile-card"><h1>내 프로필</h1><p>프로필을 보려면 <Link to="/login">로그인</Link>해 주세요.</p></section>
        : error
          ? <section className="profile-card"><p className="profile-error" role="alert">{profileError(error)}</p>
            <Button onClick={() => { setError(undefined); setAttempt(value => value + 1) }}>다시 시도</Button></section>
          : profile
            ? children(profile, token)
            : <p role="status">프로필을 불러오는 중이에요…</p>}
    </ProfileLayout>
  )
}

export function ProfileAvatar({ url }: { url: string | null }) {
  return url ? <img className="profile-avatar" src={url} alt="프로필 사진" />
    : <span className="profile-avatar" aria-hidden="true" />
}

export function ProfileProgress({ value }: { value: number | null }) {
  if (value === null) return null
  return <div className="profile-progress">
    <div><span>프로필 완성도</span><strong>{value}%</strong></div>
    <progress aria-label="프로필 완성도" value={value} max={100} />
  </div>
}

