import { useState } from 'react'
import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { StatusBadge } from '../posts/components'
import { decisionBlock, reasonLabels, statusLabels } from './model'
import type { Application, Decision, Reason, Snapshot } from './model'
import type { ManagementState } from './controller'

export function ApplicantsView({ snapshot, state, reason, onReason, onAction, onRefresh, search }: {
  snapshot: Snapshot; state: ManagementState; reason: Reason | null; onReason: (reason: Reason | null) => void;
  onAction: (id: number, decision: Decision) => void; onRefresh: () => void; search: string
}) {
  const [selected, setSelected] = useState(snapshot.roles[0]?.postRoleId ?? 0)
  const role = snapshot.roles.find(role => role.postRoleId === selected) ?? snapshot.roles[0]
  const locked = state.busy || state.loading || state.needsRefresh
  const accepted = snapshot.roles.flatMap(role => role.applications.filter(item => item.status === 'ACCEPTED').map(item => ({ ...item, roleName: role.roleName })))
  return <div className="applicants-grid">
    <div className="applicants-main">
      <p className="post-eyebrow">내 모집글 › 지원자 관리</p>
      <h2 className="applicants-post-title">{snapshot.post.title}</h2>
      <div className="applicants-toolbar"><StatusBadge status={snapshot.post.status} daysLeft={snapshot.post.dDay} /><Button disabled={state.busy || state.loading} onClick={onRefresh}>{state.loading ? '갱신 중…' : '목록·모집 현황 갱신'}</Button></div>
      <div className="applicants-role-filter" role="group" aria-label="역할별 지원자 선택">{snapshot.roles.map(item => <Button key={item.postRoleId} aria-pressed={role?.postRoleId === item.postRoleId} onClick={() => setSelected(item.postRoleId)}>{item.roleName} · 지원 {item.applications.length}</Button>)}</div>
      {state.needsRefresh && !state.loading && <p className="applicants-refresh-warning" role="status">{state.notice ? '처리는 완료됐지만 최신 목록·모집 현황을 확인하지 못했어요. 같은 요청을 반복하지 마세요.' : '최신 상태를 확인하기 전까지 처리를 잠갔어요.'} 목록·모집 현황 갱신 버튼으로 조회만 다시 시도해 주세요.</p>}
      {state.loading && <p role="status">최신 지원자 목록과 모집 현황을 불러오는 중…</p>}
      {!role ? <p className="post-message">모집 역할이 없어요.</p> : <section aria-label={`${role.roleName} 지원자`}>
        <h3 className="applicants-role-title">{role.roleName} <span>선발 {role.filledCount} / 모집 {role.headcount}명</span></h3>
        {role.applications.length === 0 ? <p className="post-message">아직 이 역할에 지원한 사람이 없어요.</p> : role.applications.map(item => <article className="applicant-card" key={item.id} aria-label={`${item.applicant.nickname ?? '이름 미등록'} 지원 내역`}>
          <div className="applicant-header"><Avatar applicant={item.applicant} />
            <div className="applicant-identity"><h3>{item.applicant.nickname ?? '이름 미등록'}</h3><p>{[item.applicant.schoolName, item.applicant.major, item.applicant.grade == null ? null : `${item.applicant.grade}학년`].filter(Boolean).join(' · ') || '공개된 기본 정보가 없어요'}</p></div>
            <span className={`post-chip applicant-status status-${item.status.toLowerCase()}`}>{statusLabels[item.status]}</span>
            <Link className="applicants-profile-link" to={`/users/${item.applicant.id}`}>프로필 보기</Link>
          </div>
          <p className="applicant-message">{item.message}</p>
          <p className="applicant-meta">가능 시간: {item.availableTime}</p>
          <p className="applicant-meta">지원 일시: <time dateTime={item.createdAt}>{item.createdAt.replace('T', ' ')}</time></p>
          <div className="applicant-actions"><Button className="applicants-soft" disabled={locked || !!decisionBlock(snapshot, item.id, 'reject')} onClick={() => onAction(item.id, 'reject')}>거절</Button><Button disabled={locked || !!decisionBlock(snapshot, item.id, 'accept')} onClick={() => onAction(item.id, 'accept')}>수락</Button></div>
          {item.status === 'PENDING' && decisionBlock(snapshot, item.id, 'accept') && <p className="applicant-meta">{decisionBlock(snapshot, item.id, 'accept')}</p>}
        </article>)}
      </section>}
    </div>
    <aside className="applicants-side" aria-label="거절 사유 및 팀 현황">
      <section className="applicant-panel"><fieldset disabled={state.busy}><legend>거절 사유 (선택 입력)</legend><div className="applicants-reasons">
        <label className={reason === null ? 'selected' : ''}><input type="radio" name="rejectReason" checked={reason === null} onChange={() => onReason(null)} />선택 안 함</label>
        {Object.entries(reasonLabels).map(([value, label]) => <label key={value} className={reason === value ? 'selected' : ''}><input type="radio" name="rejectReason" checked={reason === value} onChange={() => onReason(value as Reason)} />{label}</label>)}
      </div></fieldset><p className="applicant-meta">거절 전 확인창에서 대상과 선택한 사유를 확인할 수 있어요.</p></section>
      <section className="applicant-panel"><h2>모집 현황</h2><ul className="applicants-summary">{snapshot.roles.map(role => <li key={role.postRoleId}><strong>{role.roleName}</strong><span>선발 {role.filledCount} / 모집 {role.headcount}명</span><span>{snapshot.post.roles.find(item => item.id === role.postRoleId)?.status === 'CLOSED' ? '역할 마감' : '역할 모집 중'}</span></li>)}</ul></section>
      <section className="applicant-panel"><h2>수락된 팀원</h2>{accepted.length === 0 ? <p className="applicant-meta">아직 수락된 팀원이 없어요.</p> : <ul className="applicants-members">{accepted.map(item => <li key={item.id}><Avatar applicant={item.applicant} small /><div><Link to={`/users/${item.applicant.id}`}>{item.applicant.nickname ?? '이름 미등록'}</Link><p className="applicant-meta">{item.roleName}</p></div><span className="post-chip status-accepted">수락됨</span></li>)}</ul>}</section>
      <Link className="applicants-profile-link" to={`/posts/${snapshot.post.id}${search}`}>모집글 상세로 돌아가기</Link>
    </aside>
  </div>
}
function Avatar({ applicant, small = false }: { applicant: Application['applicant']; small?: boolean }) {
  const fallback = `${import.meta.env.BASE_URL}posts/avatar.svg`
  const value = applicant.profileImageUrl
  const src = value && (/^https?:\/\//i.test(value) || value.startsWith('/') && !value.startsWith('//')) ? value : fallback
  return <img className="applicant-avatar" src={src} width={small ? 36 : 44} height={small ? 36 : 44} alt="" onError={event => { if (!event.currentTarget.src.endsWith('/posts/avatar.svg')) event.currentTarget.src = fallback }} />
}
