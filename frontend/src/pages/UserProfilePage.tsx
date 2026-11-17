import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { useAuth } from '../features/auth/useAuth'
import { AuthStatus } from '../features/auth/AuthContext'
import { authenticatedSafety, type SafetyService } from '../features/safety/source'
import { ReportAction } from '../features/safety/ReportAction'
import { roleLabels } from '../features/posts/create-model'
import { ProfileAvatar, ProfileLayout } from '../features/profile/ProfileLayout'
import { ProfileSections } from '../features/profile/ProfileSections'
import { profileError, schoolName } from '../features/profile/presentation'
import type { Profile } from '../features/profile/api'
import '../features/profile/user-profile.css'

const validId = /^[1-9][0-9]*$/

export default function UserProfilePage() {
  const { userId = '' } = useParams()
  const [params] = useSearchParams()
  const applicationId = params.get('applicationId')
  const auth = useAuth()
  const service = useMemo(() => authenticatedSafety(auth.store, auth.session), [auth.store, auth.session])

  return <ProfileLayout>
    <div className="user-profile">
      {!validId.test(userId) || (applicationId !== null && !validId.test(applicationId))
        ? <section className="profile-card"><h1>프로필을 찾을 수 없어요.</h1></section>
        : auth.status !== 'authenticated'
          ? <section className="profile-card"><h1>사용자 프로필</h1><AuthStatus /></section>
          : <UserProfile key={`${auth.scope}:${userId}:${applicationId}`} userId={userId} applicationId={applicationId} service={service} />}
    </div>
  </ProfileLayout>
}

function UserProfile({ userId, applicationId, service }: { userId: string; applicationId: string | null; service: SafetyService }) {
  const [profile, setProfile] = useState<Profile>()
  const [error, setError] = useState<unknown>()
  const [attempt, setAttempt] = useState(0)
  const [blocked, setBlocked] = useState(false)
  const [action, setAction] = useState<'block'>()
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const actionRequest = useRef<AbortController | null>(null)
  const actionLock = useRef(false)
  useEffect(() => () => actionRequest.current?.abort(), [])

  useEffect(() => {
    const controller = new AbortController()
    service.profile(userId, applicationId, controller.signal).then(result => {
      if (controller.signal.aborted || !service.current()) return
      if (String(result.id) !== userId) {
        setError(new ApiError(404, { success: false, code: 'NOT_FOUND', message: '프로필을 찾을 수 없어요.' }))
        return
      }
      setProfile(result)
    }).catch(reason => {
      if (controller.signal.aborted || !service.current()) return
      if (reason instanceof ApiError && reason.code === 'BLOCKED_USER') setBlocked(true)
      else setError(reason)
    })
    return () => controller.abort()
  }, [attempt, service, userId, applicationId])

  function open(next: typeof action) {
    setActionError('')
    setAction(next)
  }

  async function submitBlock() {
    if (!profile || actionLock.current || !service.current()) return
    const request = new AbortController(); actionRequest.current = request; actionLock.current = true
    const current = () => !request.signal.aborted && service.current()
    setSaving(true)
    setActionError('')
    try {
      const result = await service.block(profile.id, request.signal)
      if (!current()) return
      if (result.isBlocked !== true) throw new Error('차단 결과를 확인하지 못했어요. 다시 조회해 주세요.')
      setBlocked(true)
      setAction(undefined)
    } catch (reason) {
      if (!current()) return
      if (reason instanceof ApiError && reason.code === 'ALREADY_BLOCKED') {
        setBlocked(true)
        setAction(undefined)
      } else setActionError(profileError(reason))
    } finally {
      actionLock.current = false
      if (current()) setSaving(false)
    }
  }

  async function removeBlock() {
    if (actionLock.current || !service.current()) return
    const request = new AbortController(); actionRequest.current = request; actionLock.current = true
    const current = () => !request.signal.aborted && service.current()
    setSaving(true)
    setActionError('')
    try {
      await service.remove(Number(userId), request.signal)
      if (!current()) return
      setProfile(undefined)
      setError(undefined)
      setBlocked(false)
      setAttempt(value => value + 1)
    } catch (reason) {
      if (current()) setActionError(profileError(reason))
    } finally {
      actionLock.current = false
      if (current()) setSaving(false)
    }
  }

  if (blocked) return <section className="profile-card">
    <h1>차단한 사용자예요.</h1>
    {actionError && <p role="alert" className="profile-error">{actionError}</p>}
    <Button className="profile-secondary" onClick={removeBlock} disabled={saving}>차단 해제</Button>
  </section>

  if (error) {
    const status = error instanceof ApiError ? error.status : undefined
    return <section className="profile-card">
      <h1>{status === 404 ? '프로필을 찾을 수 없어요.' : '프로필을 열 수 없어요.'}</h1>
      {status !== 404 && <p role="alert" className="profile-error">{profileError(error)}</p>}
      {status === 401
        ? <Link to="/login">다시 로그인</Link>
        : status !== 403 && status !== 404 && <Button onClick={() => {
          setError(undefined)
          setAttempt(value => value + 1)
        }}>다시 시도</Button>}
    </section>
  }

  if (!profile) return <p role="status">프로필을 불러오는 중이에요…</p>
  const displayed = applicationId ? profile : { ...profile, items: profile.items.filter(item => item.visibility === 'PUBLIC') }

  return <div className="profile-stack">
    <section className="profile-card profile-overview user-profile-overview">
      <ProfileAvatar url={profile.profileImageUrl} />
      <div className="user-profile-bio">
        <h1>{profile.nickname}</h1>
        <p className="profile-muted">{[schoolName(profile) + (profile.verified ? ' (인증)' : ''),
          profile.major, profile.grade && `${profile.grade}학년`].filter(Boolean).join(' · ')}</p>
        {profile.bio && <p>{profile.bio}</p>}
        {profile.roleTags.length > 0 && <div className="profile-pills">
          {profile.roleTags.map(role => <span key={role}>{roleLabels[role]}</span>)}
        </div>}
      </div>
      <div className="user-profile-actions">
        <ReportAction targetType="USER" targetId={profile.id} label="사용자 신고" disabled={saving} />
        <Button className="profile-secondary" disabled={saving} onClick={() => open('block')}>차단</Button>
      </div>
    </section>

    {action === 'block' && <section className="profile-card" aria-labelledby="user-block-title">
      <h2 id="user-block-title">{profile.nickname}님을 차단할까요?</h2>
      <p>서로의 대기 중인 지원은 마감되고, 차단을 해제해도 복원되지 않아요. 이미 수락된 팀은 유지돼요.</p>
      {actionError && <p className="profile-error" role="alert">{actionError}</p>}
      <div className="user-profile-actions">
        <Button className="profile-secondary" disabled={saving} onClick={() => open(undefined)}>취소</Button>
        <Button className="profile-secondary" disabled={saving} onClick={submitBlock}>{saving ? '차단 중…' : '차단하기'}</Button>
      </div>
    </section>}
    <ProfileSections profile={displayed} />
  </div>
}
