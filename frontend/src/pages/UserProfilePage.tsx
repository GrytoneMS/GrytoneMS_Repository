import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { readAccessToken } from '../features/auth/api'
import { roleLabels } from '../features/posts/create-model'
import { ProfileAvatar, ProfileLayout } from '../features/profile/ProfileLayout'
import { ProfileSections } from '../features/profile/ProfileSections'
import { profileError, schoolName } from '../features/profile/presentation'
import { blockUser, getUserProfile, reportReasons, reportUser, unblockUser } from '../features/profile/user-api'
import type { Profile } from '../features/profile/api'
import '../features/profile/user-profile.css'

const validId = /^[1-9][0-9]*$/
type ReportReason = keyof typeof reportReasons

export default function UserProfilePage() {
  const { userId = '' } = useParams()
  const [params] = useSearchParams()
  const applicationId = params.get('applicationId')
  const token = readAccessToken()

  return <ProfileLayout>
    <div className="user-profile">
      {!validId.test(userId) || (applicationId !== null && !validId.test(applicationId))
        ? <section className="profile-card"><h1>프로필을 찾을 수 없어요.</h1></section>
        : !token
          ? <section className="profile-card"><h1>사용자 프로필</h1><p><Link to="/login">로그인</Link>한 뒤 프로필을 볼 수 있어요.</p></section>
          : <UserProfile key={`${userId}:${applicationId}`} userId={userId} applicationId={applicationId} token={token} />}
    </div>
  </ProfileLayout>
}

function UserProfile({ userId, applicationId, token }: { userId: string; applicationId: string | null; token: string }) {
  const [profile, setProfile] = useState<Profile>()
  const [error, setError] = useState<unknown>()
  const [attempt, setAttempt] = useState(0)
  const [blocked, setBlocked] = useState(false)
  const [action, setAction] = useState<'report' | 'block'>()
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const [notice, setNotice] = useState('')
  const [reported, setReported] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    getUserProfile(token, userId, applicationId, controller.signal).then(result => {
      if (controller.signal.aborted) return
      if (String(result.id) !== userId) {
        setError(new ApiError(404, { success: false, code: 'NOT_FOUND', message: '프로필을 찾을 수 없어요.' }))
        return
      }
      setProfile(result)
    }).catch(reason => {
      if (controller.signal.aborted) return
      if (reason instanceof ApiError && reason.code === 'BLOCKED_USER') setBlocked(true)
      else setError(reason)
    })
    return () => controller.abort()
  }, [attempt, token, userId, applicationId])

  function open(next: typeof action) {
    setActionError('')
    setNotice('')
    setAction(next)
  }

  async function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!profile || saving || reported) return
    const fields = new FormData(event.currentTarget)
    const reason = fields.get('reason')
    if (typeof reason !== 'string' || !Object.hasOwn(reportReasons, reason)) return

    setSaving(true)
    setActionError('')
    try {
      await reportUser(token, profile.id, reason as ReportReason, String(fields.get('detail') ?? '').trim())
      setReported(true)
      setNotice('신고가 접수됐어요.')
      setAction(undefined)
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'ALREADY_REPORTED') {
        setReported(true)
        setNotice('이미 신고한 사용자예요.')
        setAction(undefined)
      } else setActionError(profileError(reason))
    } finally {
      setSaving(false)
    }
  }

  async function submitBlock() {
    if (!profile || saving) return
    setSaving(true)
    setActionError('')
    try {
      await blockUser(token, profile.id)
      setBlocked(true)
      setAction(undefined)
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'ALREADY_BLOCKED') {
        setBlocked(true)
        setAction(undefined)
      } else setActionError(profileError(reason))
    } finally {
      setSaving(false)
    }
  }

  async function removeBlock() {
    if (saving) return
    setSaving(true)
    setActionError('')
    try {
      await unblockUser(token, userId)
      setProfile(undefined)
      setError(undefined)
      setBlocked(false)
      setAttempt(value => value + 1)
    } catch (reason) {
      setActionError(profileError(reason))
    } finally {
      setSaving(false)
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
        <Button className="profile-secondary" disabled={saving || reported} onClick={() => open('report')}>신고</Button>
        <Button className="profile-secondary" disabled={saving} onClick={() => open('block')}>차단</Button>
      </div>
    </section>

    {notice && <p className="user-profile-notice" role="status">{notice}</p>}
    {action === 'report' && <section className="profile-card" aria-labelledby="user-report-title">
      <h2 id="user-report-title">사용자 신고</h2>
      <form className="user-report-form" onSubmit={submitReport}>
        <label>신고 사유
          <select name="reason" required disabled={saving} defaultValue="">
            <option value="">신고 사유를 선택해 주세요</option>
            {Object.entries(reportReasons).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select>
        </label>
        <label>상세 내용 (선택 · 200자 이하)<textarea name="detail" maxLength={200} disabled={saving} /></label>
        {actionError && <p className="profile-error" role="alert">{actionError}</p>}
        <div className="user-profile-actions">
          <Button className="profile-secondary" onClick={() => open(undefined)} disabled={saving}>취소</Button>
          <Button className="profile-secondary" type="submit" disabled={saving}>{saving ? '접수 중…' : '신고 접수'}</Button>
        </div>
      </form>
    </section>}
    {action === 'block' && <section className="profile-card" aria-labelledby="user-block-title">
      <h2 id="user-block-title">{profile.nickname}님을 차단할까요?</h2>
      {actionError && <p className="profile-error" role="alert">{actionError}</p>}
      <div className="user-profile-actions">
        <Button className="profile-secondary" disabled={saving} onClick={() => open(undefined)}>취소</Button>
        <Button className="profile-secondary" disabled={saving} onClick={submitBlock}>{saving ? '차단 중…' : '차단하기'}</Button>
      </div>
    </section>}
    <ProfileSections profile={displayed} />
  </div>
}
