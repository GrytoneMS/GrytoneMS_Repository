import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../../components/Button'
import { Input } from '../../components/Input'
import type { PostDetail } from './model'
import { applicationBlock, roleOpen } from './application-model'
import type { ApplicationDraft, ApplicationErrors, ApplyResponse } from './application-model'
import { createApplicationSubmitter } from './application-submit'
import type { ApplicationSender } from './application-submit'
import './application.css'

type Props = {
  post: PostDetail; initialRoleId: number | null; onClose: () => void;
  onSuccess: (result: ApplyResponse, draft: ApplicationDraft) => void; send?: ApplicationSender; mock: boolean
}
export function ApplicationModal({ post, initialRoleId, onClose, onSuccess, send, mock }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const submitter = useRef(createApplicationSubmitter())
  const mounted = useRef(false)
  const pending = useRef(false)
  const [draft, setDraft] = useState<ApplicationDraft>({
    postRoleId: post.roles.find(role => role.id === initialRoleId && roleOpen(role))?.id ?? null,
    message: '', availableTime: '',
  })
  const [errors, setErrors] = useState<ApplicationErrors>({})
  const [failure, setFailure] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => {
    mounted.current = true
    const element = dialog.current!
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element.showModal()
    return () => {
      mounted.current = false
      element.close()
      document.body.style.overflow = overflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  function invalid(next: ApplicationErrors) {
    setErrors(next)
    requestAnimationFrame(() => dialog.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
  }
  return createPortal(<dialog className="application-modal" ref={dialog} aria-labelledby="application-title" aria-describedby="application-post"
    onCancel={event => { event.preventDefault(); if (!pending.current) onClose() }}>
    <form noValidate onSubmit={event => {
      event.preventDefault()
      void submitter.current(post, draft, {
        invalid,
        start: () => { pending.current = true; setBusy(true); setFailure(''); setErrors({}) },
        success: (result, submitted) => { if (mounted.current) onSuccess(result, submitted) },
        failure: error => { if (mounted.current) { setFailure(error.message); invalid(error.fields) } },
        settled: () => { pending.current = false; if (mounted.current) setBusy(false) },
      }, send)
    }} aria-busy={busy}>
      <header><h2 id="application-title">지원하기</h2><Button className="application-close" aria-label="지원 모달 닫기" disabled={busy} onClick={onClose}>×</Button></header>
      <div className="application-body">
        <p id="application-post">{post.title}</p>
        {mock && <p className="application-notice" role="note">개발용 예시 · 실제 지원 요청을 보내지 않습니다.</p>}
        <fieldset disabled={busy} aria-describedby={errors.postRoleId ? 'application-role-error' : undefined}>
          <legend>지원할 역할 (1개 선택)</legend>
          <div className="application-roles">{post.roles.map(role => <label key={role.id} className={`application-role ${draft.postRoleId === role.id ? 'selected' : ''}`}>
            <input type="radio" name="postRoleId" value={role.id} checked={draft.postRoleId === role.id} disabled={!roleOpen(role)}
              aria-invalid={!!errors.postRoleId} aria-describedby={errors.postRoleId ? 'application-role-error' : undefined}
              onChange={() => { setDraft(value => ({ ...value, postRoleId: role.id })); setErrors(value => ({ ...value, postRoleId: undefined })) }} />
            <span><strong>{role.roleName}</strong><small>{roleOpen(role) ? `남은 자리 ${role.headcount - role.filledCount}` : '역할 모집 마감'}</small></span>
          </label>)}</div>
          {errors.postRoleId && <p id="application-role-error" className="field-error">{errors.postRoleId}</p>}
          <div className="input-field">
            <label htmlFor="application-message">지원 메시지 (20~300자, 필수)</label>
            <textarea id="application-message" rows={3} required value={draft.message} aria-invalid={!!errors.message}
              aria-describedby={`application-message-count${errors.message ? ' application-message-error' : ''}`}
              onChange={event => { setDraft(value => ({ ...value, message: event.target.value })); setErrors(value => ({ ...value, message: undefined })) }} />
            <small id="application-message-count">{draft.message.length} / 300</small>
            {errors.message && <p className="field-error" id="application-message-error">{errors.message}</p>}
          </div>
          <Input id="application-time" label="가능한 시간 (필수, 최대 100자)" required value={draft.availableTime} error={errors.availableTime}
            aria-describedby="application-time-count" placeholder="예) 평일 19시 이후, 주말 종일"
            onChange={event => { setDraft(value => ({ ...value, availableTime: event.target.value })); setErrors(value => ({ ...value, availableTime: undefined })) }} />
          <small id="application-time-count">{draft.availableTime.length} / 100</small>
        </fieldset>
        <section className="application-profile"><h3>팀장에게 공유되는 프로필</h3>
          <div className="post-chips">{['기본 정보', '희망 역할', '보유 기술·툴', '경력·활동', '포트폴리오'].map(label => <span className="post-chip" key={label}>{label}</span>)}</div>
          <p>실제 공개 항목은 내 프로필의 공개 설정을 따릅니다. 현재 화면에서는 개인 프로필이나 비공개 항목을 조회하지 않습니다.</p>
        </section>
        {!mock && <p className="application-notice">로그인·학교 인증 화면은 현재 준비 중입니다. 공통 인증 연동 전에는 지원 요청을 보내지 않습니다.</p>}
        {failure && <p className="field-error" role="alert">{failure}</p>}
        {busy && <p role="status">지원 요청을 보내는 중이에요. 결과 확인까지 잠시 기다려 주세요.</p>}
      </div>
      <footer><Button className="application-cancel" onClick={onClose} disabled={busy}>취소</Button><Button type="submit" disabled={busy || !!applicationBlock(post)}>{busy ? '지원 중…' : '지원하기'}</Button></footer>
    </form>
  </dialog>, document.body)
}
