import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { MockControls, PostError, RoleAvailability, RoleRequirements, StatusBadge } from '../features/posts/components'
import { categoryLabels, locationLabel, meetingLabels, postActionLabel } from '../features/posts/model'
import type { PostDetail } from '../features/posts/model'
import { isPostMock, loadPost, readScenario, readViewer } from '../features/posts/source'
import { postErrorMessage } from '../features/posts/errors'
import { ApplicationModal } from '../features/posts/ApplicationModal'
import { applicationBlock, roleOpen } from '../features/posts/application-model'
import type { ApplicationDraft, ApplyResponse } from '../features/posts/application-model'
import { mockApplicationSender } from '../features/posts/application-mock'
import { refreshAppliedPost } from '../features/posts/application-submit'

export default function PostDetailPage() {
  const { postId } = useParams()
  const [params] = useSearchParams()
  return <PostDetailContent key={`${postId}:${readScenario(params.get('scenario'))}:${readViewer(params.get('viewer'))}:${isPostMock && params.get('roles')}`} />
}

function PostDetailContent() {
  const { postId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const scenario = readScenario(params.get('scenario'))
  const viewer = readViewer(params.get('viewer'))
  const [post, setPost] = useState<PostDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [modal, setModal] = useState<{ roleId: number | null } | null>(null)
  const [notice, setNotice] = useState('')
  const [refreshFailed, setRefreshFailed] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const outcome = isPostMock ? params.get('application') : null
  const firstRoleClosed = isPostMock && params.get('roles') === 'closed'
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }, { replace: true })
  useEffect(() => {
    const request = new AbortController()
    loadPost(postId, scenario, request.signal, viewer).then(result => {
      if (!request.signal.aborted) setPost(firstRoleClosed ? { ...result, roles: result.roles.map((role, index) => index === 0 ? { ...role, status: 'CLOSED' } : role) } : result)
    })
      .catch((failure: unknown) => { if (!request.signal.aborted) setError(postErrorMessage(failure)) })
      .finally(() => { if (!request.signal.aborted) setLoading(false) })
    return () => request.abort()
  }, [postId, scenario, viewer, retry, firstRoleClosed])
  const backParams = new URLSearchParams(params); backParams.delete('viewer')
  const back = `/posts${backParams.size ? `?${backParams}` : ''}`
  const retryDetail = () => { setLoading(true); setError(''); setRetry(value => value + 1) }
  async function refreshApplication(current: PostDetail) {
    setRefreshing(true)
    const result = await refreshAppliedPost(current, async () => {
      if (isPostMock && outcome === 'refreshFailure') throw new Error('개발용 상세 재조회 실패')
      const updated = await loadPost(postId, scenario, new AbortController().signal, viewer)
      return firstRoleClosed ? { ...updated, roles: current.roles } : updated
    })
    setPost(result.post)
    setRefreshFailed(result.refreshFailed)
    setRefreshing(false)
  }
  function applicationSucceeded(result: ApplyResponse, draft: ApplicationDraft) {
    if (!post || draft.postRoleId === null) return
    const applied = { ...post, myApplication: { id: result.id, postRoleId: draft.postRoleId, status: result.status } }
    setPost(applied)
    setNotice(isPostMock ? '개발용 예시 지원이 완료됐어요. 실제 지원은 생성되지 않았습니다.' : '지원이 완료됐어요. 팀장의 응답을 기다려 주세요.')
    setModal(null)
    requestAnimationFrame(() => document.querySelector<HTMLElement>('.post-detail [data-application-notice]')?.focus())
    void refreshApplication(applied)
  }
  return <section className="post-detail" aria-label="모집글 상세">
    <MockControls scenario={scenario} onScenario={value => update('scenario', value)} viewer={viewer} onViewer={value => update('viewer', value)} />
    {isPostMock && <div className="application-examples"><label>개발용 지원 결과 예시<select value={outcome ?? 'success'} onChange={event => update('application', event.target.value)}>
      {Object.entries({ success: '지원 성공', failure: '서버 실패', refreshFailure: '성공 후 상세 재조회 실패', expired: '인증 만료', unverified: '학교 미인증', duplicate: '중복 지원', closed: '제출 직전 모집 마감', blocked: '삭제·숨김·차단', suspended: '이용 정지' }).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
    </select></label><label><input type="checkbox" checked={firstRoleClosed} onChange={event => update('roles', event.target.checked ? 'closed' : 'open')} />첫 번째 역할 모집 마감 예시</label><p>예시 모드에서는 실제 API 요청·DB 저장을 하지 않습니다.</p></div>}
    <Link className="post-back" to={back}>← 목록으로 돌아가기</Link>
    {notice && <p role="status" className="post-safety" tabIndex={-1} data-application-notice>{notice}</p>}
    {refreshFailed && post && <div className="post-message" role="status"><p>지원은 완료됐지만 최신 상세 정보를 불러오지 못했어요. 다시 지원할 필요는 없습니다.</p><Button disabled={refreshing} onClick={() => void refreshApplication(post)}>{refreshing ? '상세 갱신 중…' : '상세 정보만 다시 불러오기'}</Button></div>}
    {loading ? <p className="post-message" role="status">모집글을 불러오는 중…</p> : error ? <PostError message={error} retry={retryDetail} /> : post && <div className="post-detail-grid">
      <div className="post-detail-main">
        <div className="post-detail-hero"><div className="post-chips"><span className="post-chip">{categoryLabels[post.category]}</span><StatusBadge status={post.status} daysLeft={post.dDay} /><span className="post-chip">{meetingLabels[post.meetingType]}</span></div>
          <h1>{post.title}</h1><p className="post-deadline">모집 마감 {post.deadline} · 한국시간 23:59:59까지</p>
          <div className="post-author"><img src={safeImageUrl(post.leader.profileImageUrl) ?? `${import.meta.env.BASE_URL}posts/avatar.svg`} width="40" height="40" alt="" onError={event => { if (!event.currentTarget.src.endsWith('/posts/avatar.svg')) event.currentTarget.src = `${import.meta.env.BASE_URL}posts/avatar.svg` }} /><div><strong>{post.leader.nickname}</strong><p>{post.leader.schoolName}{post.leader.major && ` · ${post.leader.major}`}</p></div><Link className="post-soft-link" to={`/users/${post.leader.id}`}>프로필 보기</Link></div>
        </div>
        <dl className="post-facts">{[
          ['활동 기간', `${post.periodStart} ~ ${post.periodEnd}`],
          ...(locationLabel(post) ? [['모임 장소', locationLabel(post)!]] : []),
          ...(post.meetingFreq ? [['모임 빈도', post.meetingFreq]] : []),
          ['진행 방식', meetingLabels[post.meetingType]],
        ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        <section className="post-panel"><h2>프로젝트 소개</h2><p className="post-description">{post.description}</p></section>
        {post.event && <section className="post-panel post-event"><h2>행사 정보</h2><dl><div><dt>행사명</dt><dd>{post.event.name}</dd></div><div><dt>주최</dt><dd>{post.event.host}</dd></div><div><dt>접수 마감</dt><dd>{post.event.applyDeadline}</dd></div></dl>{/^https?:\/\//i.test(post.event.officialUrl) && <a href={post.event.officialUrl} target="_blank" rel="noopener noreferrer">공식 링크 열기 ↗ (새 창)</a>}</section>}
        <p className="post-safety">안전 수칙 · 첫 모임은 공개된 장소에서 하고, 개인 연락처 공유에 주의하세요.</p>
      </div>
      <aside className="post-detail-side" aria-label="역할별 모집 현황 및 지원">
        {post.roles.map(role => <section className="post-panel post-role-panel" key={role.id}>
          <RoleAvailability role={{ roleName: role.roleName, remaining: role.headcount - role.filledCount }} />
          <p>{role.status === 'CLOSED' ? '역할 모집 마감' : '역할 모집 중'} · 총 {role.headcount}명 중 {role.filledCount}명 충원</p>
          <RoleRequirements requirements={role.requirements} /><progress max={role.headcount} value={role.filledCount} aria-label={`${role.roleName} 모집 ${role.headcount}명 중 ${role.filledCount}명 충원`} />
          <Button disabled={!!applicationBlock(post) || !roleOpen(role)} onClick={() => setModal({ roleId: role.id })}>{roleOpen(role) ? `${role.roleName} 지원하기` : '역할 모집 마감'}</Button>
        </section>)}
        <Button disabled={!!applicationBlock(post)} onClick={() => setModal({ roleId: null })}>{post.myApplication || post.isMine ? postActionLabel(post) : applicationBlock(post) || '지원하기'}</Button>
        {post.isMine && <Button disabled>{post.status === 'CLOSED' ? '모집 마감됨' : '모집 마감하기 · 준비 중'}</Button>}
        <p className="post-note">{post.isMine ? '지원자 관리·마감 동작은 후속 작업입니다.' : post.myApplication ? '지원 이력이 있어 재지원할 수 없습니다. 지원 변경·연락처 조회는 후속 작업입니다.' : '지원 전 역할과 참여 가능한 시간을 확인해 주세요.'}{isPostMock && ' 현재는 개발용 예시 상태입니다.'}</p>
        <div className="post-secondary-actions"><Button disabled>스크랩 · 준비 중</Button><Button disabled>신고 · 준비 중</Button></div>
        <div className="post-contact-note"><img src={`${import.meta.env.BASE_URL}posts/lock.svg`} width="18" height="18" alt="" /><p>연락 수단은 별도 권한 확인 후 제공돼요. 이 화면에서는 조회하지 않습니다.</p></div>
      </aside>
    </div>}
    {modal && post && <ApplicationModal post={post} initialRoleId={modal.roleId} onClose={() => setModal(null)} onSuccess={applicationSucceeded}
      mock={isPostMock} send={isPostMock ? mockApplicationSender(outcome) : undefined} />}
  </section>
}

function safeImageUrl(value: string | null) {
  if (!value) return null
  if (/^https?:\/\//i.test(value) || value.startsWith('/') && !value.startsWith('//')) return value
  return null
}
