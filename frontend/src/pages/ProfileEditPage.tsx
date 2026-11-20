import { useEffect, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { roleLabels } from '../features/posts/create-model'
import type { RoleCode } from '../features/posts/model'
import {
  deleteItem, getNicknameChange, saveActivityVisibility, saveBasics, saveImage, saveItem, saveNickname, saveRoleTags, saveSkills,
} from '../features/profile/api'
import type { ItemInput, ItemType, Profile } from '../features/profile/api'
import { ItemEditor } from '../features/profile/ItemEditor'
import { MyProfileData, ProfileAvatar, ProfileProgress } from '../features/profile/ProfileLayout'
import { itemLabels, portfolioLink, profileError, schoolName } from '../features/profile/presentation'

type DraftItem = ItemInput & { key: number; id?: number }
const roleCodes = Object.keys(roleLabels) as RoleCode[]
const itemTypes = Object.keys(itemLabels) as ItemType[]
const same = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right)

function itemBody(item: ItemInput): ItemInput {
  const clean = (value?: string | null) => value?.trim() || null
  const base = { itemType: item.itemType, title: item.title.trim(), visibility: item.visibility }
  if (item.itemType === 'PORTFOLIO') return { ...base, url: clean(item.url) }
  if (item.itemType === 'EXPERIENCE') return {
    ...base, subType: item.subType, role: clean(item.role), startDate: item.startDate,
    endDate: item.endDate ?? null, description: clean(item.description),
  }
  return { ...base, issuer: clean(item.issuer), startDate: item.startDate }
}

export default function ProfileEditPage() {
  return <MyProfileData>{(profile, token) => <ProfileEditor initial={profile} token={token} />}</MyProfileData>
}

function ProfileEditor({ initial, token }: { initial: Profile; token: string }) {
  const navigate = useNavigate()
  const [original, setOriginal] = useState(initial)
  const [nickname, setNickname] = useState(initial.nickname)
  const [nicknameDate, setNicknameDate] = useState<string | null>()
  const [major, setMajor] = useState(initial.major ?? '')
  const [grade, setGrade] = useState(initial.grade?.toString() ?? '')
  const [bio, setBio] = useState(initial.bio ?? '')
  const [roles, setRoles] = useState(initial.roleTags)
  const [skills, setSkills] = useState(initial.skills)
  const [skillInput, setSkillInput] = useState('')
  const [skillError, setSkillError] = useState('')
  const [items, setItems] = useState<DraftItem[]>(() => initial.items.map(item => ({ ...item, key: item.id })))
  const [showActivity, setShowActivity] = useState(initial.showActivity ?? true)
  const [image, setImage] = useState<File | null>()
  const [imageError, setImageError] = useState('')
  const [error, setError] = useState('')
  const [expired, setExpired] = useState(false)
  const [saving, setSaving] = useState(false)
  const nextKey = useRef(-1)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const controller = new AbortController()
    getNicknameChange(token, controller.signal).then(user => {
      if (!controller.signal.aborted) setNicknameDate(user.nicknameChangeableAt)
    }).catch(() => { /* 닉네임 변경 가능 여부는 저장 시 서버에서도 확인합니다. */ })
    return () => controller.abort()
  }, [token])

  function toggleRole(role: RoleCode) {
    setRoles(current => current.includes(role) ? current.filter(value => value !== role)
      : current.length < 3 ? [...current, role] : current)
  }

  function appendSkill(value: string) {
    const skill = value.trim()
    if (!skill) return skills
    if (skills.some(existing => existing.toLowerCase() === skill.toLowerCase())) return skills
    if (skills.length >= 10) return null
    return [...skills, skill]
  }

  function addSkill(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    event.preventDefault()
    const next = appendSkill(skillInput)
    if (!next) {
      setSkillError('기술·툴은 최대 10개까지 추가할 수 있어요.')
      return
    }
    setSkills(next)
    setSkillInput('')
    setSkillError('')
  }

  function addItem(itemType: ItemType) {
    const key = nextKey.current--
    setItems(current => [...current, {
      key, itemType, title: '', visibility: 'PUBLIC',
      ...(itemType === 'EXPERIENCE' ? { subType: 'CLUB' } : {}),
    }])
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving || expired) return
    setError('')
    const nextSkills = appendSkill(skillInput)
    const basics = { major: major.trim() || null, grade: grade ? Number(grade) : null, bio: bio.trim() || null }
    const trimmedNickname = nickname.trim()
    const pendingItems = items.map(item => ({ ...item, body: itemBody(item) }))
    if (!/^[가-힣A-Za-z0-9]{2,12}$/.test(trimmedNickname)) {
      setError('닉네임은 한글·영문·숫자로 2~12자 입력해 주세요.')
      return
    }
    if (!nextSkills) {
      setSkillError('기술·툴은 최대 10개까지 추가할 수 있어요.')
      return
    }
    for (const { body } of pendingItems) {
      if (!body.title || (!body.issuer && (body.itemType === 'AWARD' || body.itemType === 'CERTIFICATE'))) {
        setError('이력의 필수 항목을 입력해 주세요.')
        return
      }
      if (body.itemType === 'PORTFOLIO' && !portfolioLink(body.url)) {
        setError('포트폴리오 주소는 http:// 또는 https://로 시작해야 해요.')
        return
      }
    }

    setSaving(true)
    setSkills(nextSkills)
    setSkillInput('')
    setSkillError('')
    let step = '닉네임'
    let saved = 0
    try {
      if (trimmedNickname !== original.nickname) {
        const result = await saveNickname(token, trimmedNickname)
        setOriginal(current => ({ ...current, nickname: result.nickname }))
        setNickname(result.nickname)
        setNicknameDate(result.nicknameChangeableAt)
        saved++
      }
      step = '기본 정보'
      if (!same(basics, { major: original.major, grade: original.grade, bio: original.bio })) {
        const result = await saveBasics(token, basics)
        setOriginal(current => ({ ...current, ...basics, completeness: result.completeness }))
        saved++
      }
      step = '희망 역할'
      if (!same(roles, original.roleTags)) {
        const result = await saveRoleTags(token, roles)
        setOriginal(current => ({ ...current, roleTags: result.roleTags, completeness: result.completeness }))
        setRoles(result.roleTags)
        saved++
      }
      step = '보유 기술·툴'
      if (!same(nextSkills, original.skills)) {
        const result = await saveSkills(token, nextSkills)
        setOriginal(current => ({ ...current, skills: result.skills, completeness: result.completeness }))
        setSkills(result.skills)
        saved++
      }
      step = '프로필 이미지'
      if (image !== undefined) {
        const result = await saveImage(token, image ?? undefined)
        setOriginal(current => ({ ...current, profileImageUrl: result.profileImageUrl }))
        setImage(undefined)
        if (fileInput.current) fileInput.current.value = ''
        saved++
      }

      for (const previous of original.items) {
        if (items.some(item => item.id === previous.id)) continue
        step = `이력 삭제 (${previous.title})`
        await deleteItem(token, previous.id)
        setOriginal(current => ({ ...current, items: current.items.filter(item => item.id !== previous.id) }))
        saved++
      }
      for (const item of pendingItems) {
        const previous = original.items.find(entry => entry.id === item.id)
        if (previous && same(itemBody(previous), item.body)) continue
        step = `이력 저장 (${item.body.title})`
        const result = await saveItem(token, item.body, item.id)
        setOriginal(current => ({ ...current, items: [...current.items.filter(entry => entry.id !== result.id), result] }))
        setItems(current => current.map(entry => entry.key === item.key ? { ...result, key: entry.key } : entry))
        saved++
      }
      step = '서비스 활동 이력 공개 범위'
      if (showActivity !== (original.showActivity ?? true)) {
        await saveActivityVisibility(token, showActivity)
        saved++
      }
      navigate('/me/profile', { replace: true })
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) setExpired(true)
      setError(`${step} 처리 중 오류가 발생했어요. ${profileError(reason)}${saved ? ' 앞에서 저장된 변경은 반영됐어요.' : ''}`)
    } finally {
      setSaving(false)
    }
  }

  return <form onSubmit={submit} className="profile-edit-grid" aria-busy={saving}>
    <fieldset className="profile-stack profile-edit-fields" disabled={saving || expired}>
      <h1>프로필 편집</h1>
      <section className="profile-card">
        <h2>기본 정보 (항상 공개)</h2>
        <p className="profile-muted">프로필 이미지 · JPG·PNG, 5MB 이하</p>
        <div className="profile-image-controls">
          <ProfileAvatar url={image === null ? null : original.profileImageUrl} />
          <Button className="profile-secondary" onClick={() => fileInput.current?.click()}>이미지 변경</Button>
          {(image || original.profileImageUrl) && image !== null && <Button className="profile-secondary" onClick={() => {
            setImage(original.profileImageUrl ? null : undefined)
            setImageError('')
            if (fileInput.current) fileInput.current.value = ''
          }}>이미지 삭제</Button>}
          <input ref={fileInput} type="file" accept="image/jpeg,image/png" hidden onChange={event => {
            const file = event.currentTarget.files?.[0]
            if (!file) return
            if (!['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024) {
              setImageError('JPG 또는 PNG 형식의 5MB 이하 이미지를 선택해 주세요.')
              event.currentTarget.value = ''
              return
            }
            setImageError('')
            setImage(file)
          }} />
        </div>
        {image && <p className="profile-muted">{image.name}</p>}
        {image === null && <p className="profile-muted">저장하면 이미지가 삭제돼요.</p>}
        {imageError && <p className="profile-error" role="alert">{imageError}</p>}
        <div className="profile-fields">
          <Input label="닉네임 (2~12자)" value={nickname} required minLength={2} maxLength={12} onChange={event => setNickname(event.target.value)} />
          <Input label={initial.verified ? '학교 (인증)' : '학교'} readOnly value={schoolName(initial)} />
          <p className="profile-muted profile-wide">닉네임은 30일에 한 번 변경할 수 있어요.{nicknameDate && ` 다음 변경 가능일 ${nicknameDate}`}</p>
          <Input label="학과" value={major} maxLength={50} onChange={event => setMajor(event.target.value)} />
          <label>학년<select value={grade} onChange={event => setGrade(event.target.value)}>
            <option value="">선택 안 함</option>
            {[1, 2, 3, 4, 5, 6].map(value => <option key={value} value={value}>{value}학년</option>)}
          </select></label>
          <label className="profile-wide">한 줄 소개 (50자)
            <input className="input" value={bio} maxLength={50} onChange={event => setBio(event.target.value)} />
          </label>
        </div>
      </section>
      <section className="profile-card">
        <h2>희망 역할 (최대 3개, 항상 공개)</h2>
        <div className="profile-pills" role="group" aria-label="희망 역할">{roleCodes.map(role => {
          const selected = roles.includes(role)
          return <button key={role} type="button" aria-pressed={selected}
            disabled={!selected && roles.length >= 3} onClick={() => toggleRole(role)}>{roleLabels[role]}</button>
        })}</div>
        <h2 className="profile-skills-title">보유 기술·툴 (최대 10개)</h2>
        <div className="profile-pills profile-blue-pills">{skills.map(skill =>
          <button type="button" key={skill} aria-label={`${skill} 삭제`} onClick={() => {
            setSkills(current => current.filter(value => value !== skill))
            setSkillError('')
          }}>{skill} ×</button>)}</div>
        <Input label="기술·툴 추가" placeholder="입력 후 Enter" value={skillInput} maxLength={30}
          error={skillError} onChange={event => { setSkillInput(event.target.value); setSkillError('') }} onKeyDown={addSkill} />
      </section>
      {itemTypes.map(type => {
        const group = items.filter(item => item.itemType === type)
        return <section className="profile-card" key={type}>
          <div className="profile-heading"><h2>{itemLabels[type]}{type === 'PORTFOLIO' && ' (최대 5개)'}</h2>
            <Button className="profile-secondary" disabled={type === 'PORTFOLIO' && group.length >= 5}
              onClick={() => addItem(type)} aria-label={`${itemLabels[type]} 추가`}>추가</Button></div>
          {group.map(item => <ItemEditor key={item.key} item={item}
            change={patch => setItems(current => current.map(entry => entry.key === item.key ? { ...entry, ...patch } : entry))}
            remove={() => setItems(current => current.filter(entry => entry.key !== item.key))} />)}
          {!group.length && <p className="profile-muted">등록한 항목이 없어요.</p>}
        </section>
      })}
    </fieldset>
    <aside className="profile-stack profile-edit-aside">
      <section className="profile-card">
        <ProfileProgress value={original.completeness} />
        <p className="profile-muted">70% 미만이면 지원 화면에서 프로필 작성을 안내해요.</p>
        {error && <p className="profile-error" role="alert">{error}</p>}
        {expired ? <Link to="/login">다시 로그인</Link>
          : <Button className="profile-primary" type="submit" disabled={saving}>{saving ? '저장 중…' : '저장'}</Button>}
        <Link className="profile-back" to="/me/profile">내 프로필로 돌아가기</Link>
      </section>
      <section className="profile-card">
        <h2>서비스 활동 이력</h2>
        <p className="profile-muted">참여한 팀 이력은 자동으로 쌓여요. 프로필 노출 여부를 선택할 수 있어요.</p>
        <label className="profile-heading"><span>프로필에 노출</span>
          <input type="checkbox" role="switch" checked={showActivity} disabled={saving || expired}
            onChange={event => setShowActivity(event.target.checked)} />
        </label>
      </section>
    </aside>
  </form>
}
