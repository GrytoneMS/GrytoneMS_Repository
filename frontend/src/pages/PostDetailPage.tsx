import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { MockControls, PostError, RoleAvailability, StatusBadge } from '../features/posts/components'
import { categoryLabels, locationLabel, meetingLabels, postActionLabel } from '../features/posts/model'
import type { PostDetail } from '../features/posts/model'
import { isPostMock, loadPost, readScenario, readViewer } from '../features/posts/source'
import { postErrorMessage } from '../features/posts/errors'

export default function PostDetailPage() {
  const { postId } = useParams()
  const [params] = useSearchParams()
  return <PostDetailContent key={`${postId}:${readScenario(params.get('scenario'))}:${readViewer(params.get('viewer'))}`} />
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
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }, { replace: true })
  useEffect(() => {
    const request = new AbortController()
    loadPost(postId, scenario, request.signal, viewer).then(result => { if (!request.signal.aborted) setPost(result) })
      .catch((failure: unknown) => { if (!request.signal.aborted) setError(postErrorMessage(failure)) })
      .finally(() => { if (!request.signal.aborted) setLoading(false) })
    return () => request.abort()
  }, [postId, scenario, viewer, retry])
  const backParams = new URLSearchParams(params); backParams.delete('viewer')
  const back = `/posts${backParams.size ? `?${backParams}` : ''}`
  const retryDetail = () => { setLoading(true); setError(''); setRetry(value => value + 1) }
  return <section className="post-detail" aria-label="모집글 상세">
    <MockControls scenario={scenario} onScenario={value => update('scenario', value)} viewer={viewer} onViewer={value => update('viewer', value)} />
    <Link className="post-back" to={back}>← 목록으로 돌아가기</Link>
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
          <p>{role.requirements}</p><progress max={role.headcount} value={role.filledCount} aria-label={`${role.roleName} 모집 ${role.headcount}명 중 ${role.filledCount}명 충원`} />
        </section>)}
        <Button disabled>{postActionLabel(post)}</Button>
        {post.isMine && <Button disabled>{post.status === 'CLOSED' ? '모집 마감됨' : '모집 마감하기 · 준비 중'}</Button>}
        <p className="post-note">{post.isMine ? '지원자 관리·마감 동작은 후속 작업입니다.' : post.myApplication ? '지원 상태는 조회 결과입니다. 지원 변경·연락처 조회는 이번 구현 범위에 포함되지 않습니다.' : '지원 모달과 로그인·학교 인증 연결은 후속 작업입니다.'}{isPostMock && ' 현재는 개발용 예시 상태입니다.'}</p>
        <div className="post-secondary-actions"><Button disabled>스크랩 · 준비 중</Button><Button disabled>신고 · 준비 중</Button></div>
        <div className="post-contact-note"><img src={`${import.meta.env.BASE_URL}posts/lock.svg`} width="18" height="18" alt="" /><p>연락 수단은 별도 권한 확인 후 제공돼요. 이 화면에서는 조회하지 않습니다.</p></div>
      </aside>
    </div>}
  </section>
}

function safeImageUrl(value: string | null) {
  if (!value) return null
  if (/^https?:\/\//i.test(value) || value.startsWith('/') && !value.startsWith('//')) return value
  return null
}
