import { useEffect, useRef, useState } from 'react'
import type { ComponentPropsWithoutRef, ReactNode, SubmitEvent } from 'react'
import { useNavigate } from 'react-router'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { createFailure, emptyRole, initialPostDraft, needsEvent, needsLocationText, roleLabels, todayInKorea } from '../features/posts/create-model'
import { createPostSubmitter } from '../features/posts/create-submit'
import { CreateIntegrationNotice } from '../features/posts/CreateIntegrationNotice'
import type { CreateErrors, PostDraft, RoleDraft } from '../features/posts/create-model'
import { categoryLabels, locationLabels, meetingLabels } from '../features/posts/model'
import '../features/posts/create.css'

function Field({ field, label, error, children }: { field: string; label: string; error?: string; children: ReactNode }) {
  return <div className="input-field"><label htmlFor={`create-${field}`}>{label}</label>{children}{error && <p className="field-error" id={`create-${field}-error`}>{error}</p>}</div>
}
function Choices<T extends string>({ field, label, value, options, onChange, error }: {
  field: string; label: string; value: T | ''; options: Record<T, string>; onChange: (value: T) => void; error?: string
}) {
  return <fieldset className="create-choices" aria-describedby={error ? `create-${field}-error` : undefined}>
    <legend>{label}</legend><div className="create-chips">{Object.entries<string>(options).map(([key, text], index) => <label className="create-chip" key={key}>
      <input id={index === 0 ? `create-${field}` : `create-${field}-${key}`} name={field} type="radio" value={key} checked={value === key} onChange={() => onChange(key as T)} aria-invalid={!!error} />
      <span>{text}</span>
    </label>)}</div>{error && <p className="field-error" id={`create-${field}-error`}>{error}</p>}
  </fieldset>
}

export default function PostCreatePage() {
  const navigate = useNavigate()
  const [draft, setDraft] = useState(initialPostDraft)
  const [errors, setErrors] = useState<CreateErrors>({})
  const [failure, setFailure] = useState<ReturnType<typeof createFailure> | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const submitter = useRef<ReturnType<typeof createPostSubmitter> | null>(null)
  if (submitter.current === null) submitter.current = createPostSubmitter()
  const nextRoleKey = useRef(1)
  const form = useRef<HTMLFormElement>(null)
  useEffect(() => {
    const current = submitter.current
    return () => current?.cancel()
  }, [])
  function change(update: (previous: PostDraft) => PostDraft) {
    setDraft(update); setErrors({}); setFailure(null)
  }
  function set<K extends keyof PostDraft>(field: K, value: PostDraft[K]) { change(previous => ({ ...previous, [field]: value })) }
  function setRole<K extends keyof RoleDraft>(key: number, field: K, value: RoleDraft[K]) {
    change(previous => ({ ...previous, roles: previous.roles.map(role => role.key === key ? { ...role, [field]: value } : role) }))
  }
  function setEvent(field: keyof PostDraft['event'], value: string) { change(previous => ({ ...previous, event: { ...previous.event, [field]: value } })) }
  function focusField(field: string) {
    const target = document.getElementById(`create-${field}`) ?? form.current?.querySelector<HTMLElement>('.create-error-summary')
    target?.focus(); target?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }
  function showErrors(next: CreateErrors) {
    setErrors(next)
    requestAnimationFrame(() => {
      const first = form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')
      if (first) { first.focus(); first.scrollIntoView({ block: 'center', behavior: 'smooth' }) }
      else form.current?.querySelector<HTMLElement>('.create-error-summary')?.focus()
    })
  }
  async function submit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()
    await submitter.current!.submit(draft, {
      onInvalid: next => { setFailure(null); showErrors(next) },
      onStart: () => { setFailure(null); setErrors({}); setSubmitting(true) },
      onSuccess: id => navigate(`/posts/${id}`),
      onFailure: result => { setFailure(result); showErrors(result.fields) },
      onSettled: () => setSubmitting(false),
    })
  }
  const input = (field: string, label: string, value: string, onChange: (value: string) => void, props: ComponentPropsWithoutRef<'input'> = {}) =>
    <Input {...props} id={`create-${field}`} name={field} label={label} value={value} onChange={event => onChange(event.target.value)} error={errors[field]} />
  const attributes = (field: string) => ({ id: `create-${field}`, name: field, 'aria-invalid': !!errors[field], 'aria-describedby': errors[field] ? `create-${field}-error` : undefined })
  const today = todayInKorea()
  return <form ref={form} className="post-create" onSubmit={submit} noValidate aria-busy={submitting}>
    <div className="create-main">
      <h1>모집글 작성</h1>
      <p className="create-caption">필수 항목을 입력해 주세요. 요구 조건은 선택 사항이에요.</p>
      <CreateIntegrationNotice />
      {(failure || Object.keys(errors).length > 0) && <div className="create-error-summary" tabIndex={-1} role="alert">
        <p>{failure?.message ?? '입력 내용을 확인해 주세요.'}</p>
        {Object.keys(errors).length > 0 && <ul>{Object.entries(errors).map(([field, message]) => <li key={field}><button type="button" onClick={() => focusField(field)}>{message}</button></li>)}</ul>}
      </div>}
      <fieldset className="create-fields" disabled={submitting}>
        <legend className="create-sr-only">모집글 입력</legend>
        <section className="post-panel create-section" aria-labelledby="create-basic-heading">
          <h2 id="create-basic-heading">기본 정보</h2>
          <Choices field="category" label="유형 (필수)" value={draft.category} options={categoryLabels} onChange={value => set('category', value)} error={errors.category} />
          {input('title', '제목 (5~40자, 필수)', draft.title, value => set('title', value), { required: true, minLength: 5, maxLength: 40, placeholder: '예) ○○ 영상 공모전 팀원 구해요' })}
          <Field field="description" label="프로젝트 소개 (20~1,000자, 필수)" error={errors.description}>
            <textarea {...attributes('description')} required minLength={20} maxLength={1000} rows={4} className="input" value={draft.description} onChange={event => set('description', event.target.value)} placeholder="함께할 활동과 목표를 소개해 주세요." aria-describedby={['create-description-count', errors.description ? 'create-description-error' : ''].filter(Boolean).join(' ')} />
            <p className="create-caption" id="create-description-count">{draft.description.length.toLocaleString()} / 1,000</p>
          </Field>
        </section>
        <section className="post-panel create-section" aria-labelledby="create-roles-heading">
          <div className="create-section-heading"><h2 id="create-roles-heading">모집 역할 (최대 5개)</h2><span className="create-caption">{draft.roles.length} / 5</span></div>
          {errors.roles && <p className="field-error">{errors.roles}</p>}
          {draft.roles.map((role, index) => {
            const field = `roles[${index}]`
            return <fieldset className="create-role" key={role.key}>
              <legend>역할 {index + 1}</legend>
              <div className="create-role-grid">
                <Field field={`${field}.roleCode`} label="역할 분류 (필수)" error={errors[`${field}.roleCode`]}>
                  <select {...attributes(`${field}.roleCode`)} className="input" required value={role.roleCode} onChange={event => setRole(role.key, 'roleCode', event.target.value as RoleDraft['roleCode'])}>
                    <option value="">선택해 주세요</option>{Object.entries(roleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                  </select>
                </Field>
                {input(`${field}.roleName`, '역할명 (30자 이내, 필수)', role.roleName, value => setRole(role.key, 'roleName', value), { required: true, maxLength: 30, placeholder: '예) 영상 편집' })}
                {input(`${field}.headcount`, '인원 (1~5명, 필수)', role.headcount, value => setRole(role.key, 'headcount', value), { type: 'number', required: true, min: 1, max: 5, step: 1, inputMode: 'numeric' })}
                {input(`${field}.requirements`, '요구 조건 (100자 이내, 선택)', role.requirements, value => setRole(role.key, 'requirements', value), { maxLength: 100, placeholder: '예) Premiere 가능자 우대' })}
              </div>
              <Button className="create-soft" disabled={draft.roles.length === 1} aria-label={`역할 ${index + 1} 삭제`} onClick={() => change(previous => ({ ...previous, roles: previous.roles.filter(item => item.key !== role.key) }))}>삭제</Button>
            </fieldset>
          })}
          <Button className="create-outline" disabled={draft.roles.length >= 5} onClick={() => {
            const key = nextRoleKey.current++
            change(previous => previous.roles.length >= 5 ? previous : ({ ...previous, roles: [...previous.roles, emptyRole(key)] }))
            requestAnimationFrame(() => focusField(`roles[${draft.roles.length}].roleCode`))
          }}>+ 역할 추가</Button>
        </section>
        <section className="post-panel create-section" aria-labelledby="create-dates-heading">
          <h2 id="create-dates-heading">일정</h2>
          <div className="create-grid">
            {input('periodStart', '활동 시작일 (필수)', draft.periodStart, value => set('periodStart', value), { type: 'date', required: true, min: today, max: '9999-12-31' })}
            {input('periodEnd', '활동 종료일 (필수)', draft.periodEnd, value => set('periodEnd', value), { type: 'date', required: true, min: draft.periodStart || today, max: '9999-12-31' })}
            {input('deadline', '모집 마감일 (필수)', draft.deadline, value => set('deadline', value), { type: 'date', required: true, min: today, max: draft.periodStart || '9999-12-31' })}
          </div><p className="create-caption">모집 마감일은 오늘 또는 이후, 활동 시작일과 같거나 이전이어야 해요.</p>
        </section>
        <section className="post-panel create-section" aria-labelledby="create-meeting-heading">
          <h2 id="create-meeting-heading">진행 방식 · 모임 장소</h2>
          <Choices field="meetingType" label="진행 방식 (필수)" value={draft.meetingType} options={meetingLabels} onChange={value => set('meetingType', value)} error={errors.meetingType} />
          {draft.meetingType !== 'ONLINE' && <>
            <Choices field="locationCode" label="모임 장소 (대면·혼합일 때 필수)" value={draft.locationCode} options={locationLabels} onChange={value => set('locationCode', value)} error={errors.locationCode} />
            {needsLocationText(draft.locationCode) && input('locationText', '상세 장소 (100자 이내, 필수)', draft.locationText, value => set('locationText', value), { maxLength: 100, required: true, placeholder: draft.locationCode === 'CAMPUS' ? '대학교·캠퍼스 이름을 입력해 주세요' : '모임 장소를 입력해 주세요' })}
            {input('meetingFreq', '모임 빈도 (30자 이내, 필수)', draft.meetingFreq, value => set('meetingFreq', value), { maxLength: 30, required: true, placeholder: '예) 주 1회' })}
          </>}
        </section>
        {needsEvent(draft.category) && <section className="post-panel create-section create-event" aria-labelledby="create-event-heading">
          <h2 id="create-event-heading">행사 정보 (공모전·대회일 때 필수)</h2>
          <div className="create-grid">
            {input('event.name', '행사명 (100자 이내, 필수)', draft.event.name, value => setEvent('name', value), { required: true, maxLength: 100 })}
            {input('event.host', '주최 (100자 이내, 필수)', draft.event.host, value => setEvent('host', value), { required: true, maxLength: 100 })}
            {input('event.officialUrl', '공식 링크 (필수)', draft.event.officialUrl, value => setEvent('officialUrl', value), { type: 'url', required: true, maxLength: 500, placeholder: 'https://' })}
            {input('event.applyDeadline', '접수 마감일 (필수)', draft.event.applyDeadline, value => setEvent('applyDeadline', value), { type: 'date', required: true, max: '9999-12-31' })}
          </div>
        </section>}
        <section className="post-panel create-section" aria-labelledby="create-contact-heading">
          <h2 id="create-contact-heading">연락 수단</h2>
          {input('contactLink', '오픈채팅 등 URL (필수)', draft.contactLink, value => set('contactLink', value), { type: 'url', required: true, maxLength: 500, placeholder: 'https://open.kakao.com/…', 'aria-describedby': 'create-contact-note' })}
          <p className="create-caption" id="create-contact-note">수락된 지원자에게만 공개돼요.</p>
        </section>
      </fieldset>
    </div>
    <aside className="post-panel create-submit" aria-labelledby="create-submit-heading">
      <h2 id="create-submit-heading">등록</h2>
      <p className="create-caption">모집 중인 글은 1인당 최대 3개예요. 초과하면 기존 글을 마감한 뒤 등록해 주세요.</p>
      <p className="create-caption">모집글 등록에는 로그인과 학교 인증이 필요해요.</p>
      <Button type="submit" disabled={submitting}>{submitting ? '등록 중…' : '모집글 등록'}</Button>
      <p className="create-caption" aria-live="polite">{submitting ? '등록 결과를 기다리고 있어요.' : '필수 항목이 비어 있으면 해당 칸으로 이동해 안내해요.'}</p>
    </aside>
  </form>
}
