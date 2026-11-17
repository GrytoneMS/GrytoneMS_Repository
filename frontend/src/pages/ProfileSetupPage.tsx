import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { BrandLogo } from '../components/BrandLogo'
import { Input } from '../components/Input'
import { authErrors, readAccessToken } from '../features/auth/api'
import { getMyProfile, saveRoleTags } from '../features/profile/api'
import { roleLabels } from '../features/posts/create-model'
import type { RoleCode } from '../features/posts/model'
import '../features/profile/setup.css'

const roles = Object.keys(roleLabels) as RoleCode[]
const maxRoles = 3

export default function ProfileSetupPage() {
  const token = readAccessToken()

  return (
    <div className="profile-setup">
      <header><Link to="/posts" aria-label="TEAM:ON 홈"><BrandLogo /></Link></header>
      <section className="profile-setup-card" aria-labelledby="profile-setup-title">
        <h1 id="profile-setup-title">프로필 설정</h1>
        {token
          ? <ProfileSetupForm token={token} />
          : <p>프로필을 설정하려면 <Link to="/login">로그인</Link>해 주세요.</p>}
      </section>
    </div>
  )
}

function ProfileSetupForm({ token }: { token: string }) {
  const navigate = useNavigate()
  const [nickname, setNickname] = useState('')
  const [roleCodes, setRoleCodes] = useState<RoleCode[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [expired, setExpired] = useState(false)
  const [saving, setSaving] = useState(false)
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getMyProfile(token, controller.signal).then(profile => {
      if (controller.signal.aborted) return
      setNickname(profile.nickname)
      setRoleCodes(profile.roleTags)
    }).catch(error => {
      if (controller.signal.aborted) return
      if (error instanceof ApiError && error.status === 401) setExpired(true)
      else setLoadError(authErrors(error).message ?? '프로필을 불러오지 못했어요.')
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [token, retry])

  function toggleRole(roleCode: RoleCode) {
    setSaveError('')
    setRoleCodes(current => {
      if (current.includes(roleCode)) return current.filter(role => role !== roleCode)
      return current.length < maxRoles ? [...current, roleCode] : current
    })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving || roleCodes.length === 0 || roleCodes.length > maxRoles) return

    setSaving(true)
    setSaveError('')
    try {
      await saveRoleTags(token, roleCodes)
      navigate('/posts', { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) setExpired(true)
      else {
        const feedback = authErrors(error)
        setSaveError(feedback.fields?.roleCodes ?? feedback.message ?? '희망 역할을 저장하지 못했어요.')
      }
    } finally {
      setSaving(false)
    }
  }

  if (expired) return <p>로그인 시간이 만료됐어요. <Link to="/login">다시 로그인</Link>해 주세요.</p>
  if (loading) return <p role="status">프로필을 불러오는 중이에요…</p>
  if (loadError) return <>
    <p className="profile-setup-error" role="alert">{loadError}</p>
    <Button onClick={() => {
      setLoadError('')
      setLoading(true)
      setRetry(value => value + 1)
    }}>다시 시도</Button>
  </>

  return (
    <form onSubmit={save} aria-busy={saving}>
      <p className="profile-setup-help">가입 후 한 번만 설정해요. 학과·학년·한 줄 소개는 마이페이지에서 나중에 채울 수 있어요.</p>
      <Input label="닉네임 (필수 · 2~12자, 한글·영문·숫자)" value={nickname}
        readOnly aria-describedby="setup-nickname-help" />
      <div className="profile-setup-nickname">
        <span id="setup-nickname-help">가입할 때 설정한 닉네임이에요.</span>
        <span>{nickname.length} / 12</span>
      </div>
      <p className="profile-setup-help">닉네임은 설정 후 30일에 1회 변경할 수 있어요.</p>

      <div className="profile-setup-role-heading">
        <h2 id="setup-roles">희망 역할 (선택 · 최대 {maxRoles}개)</h2>
        <button className="profile-setup-skip" type="button" disabled={saving}
          onClick={() => navigate('/posts', { replace: true })}>건너뛰기</button>
      </div>
      <div className="profile-setup-roles" role="group" aria-labelledby="setup-roles"
        aria-describedby="setup-role-help">
        {roles.map(role => {
          const selected = roleCodes.includes(role)
          return (
            <button key={role} type="button" aria-pressed={selected}
              disabled={saving || (!selected && roleCodes.length >= maxRoles)}
              onClick={() => toggleRole(role)}>{roleLabels[role]}</button>
          )
        })}
      </div>
      <p className="profile-setup-help" id="setup-role-help" aria-live="polite">
        {roleCodes.length >= maxRoles
          ? '최대 3개를 선택했어요. 바꾸려면 선택한 역할을 한 번 더 눌러 주세요.'
          : '1개 이상 선택해 두면 좋아요. 건너뛰어도 나중에 마이페이지에서 바꿀 수 있어요.'}
      </p>
      {saveError && <p className="profile-setup-error" role="alert">{saveError}</p>}
      <Button className="profile-setup-start" type="submit" disabled={saving || roleCodes.length === 0}>
        {saving ? '저장 중…' : '시작하기'}
      </Button>
    </form>
  )
}
