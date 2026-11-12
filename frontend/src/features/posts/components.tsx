import { Link } from 'react-router'
import { Button } from '../../components/Button'
import { categoryLabels, meetingLabels } from './model'
import type { PostRole, PostStatus, PostSummary, RoleSummary, Viewer } from './model'
import { isPostMock, type Scenario } from './source'

export function RoleRequirements({ requirements }: Pick<PostRole, 'requirements'>) {
  return <p>{requirements?.trim() ? requirements : '요구조건 없음'}</p>
}
export function StatusBadge({ status, daysLeft }: { status: PostStatus; daysLeft: number }) {
  return <span className={`post-chip ${status === 'CLOSED' ? 'post-chip-closed' : daysLeft <= 3 ? 'post-chip-urgent' : ''}`}>{status === 'CLOSED' ? '모집 마감' : daysLeft === 0 ? 'D-Day' : daysLeft < 0 ? `마감일 ${Math.abs(daysLeft)}일 경과` : `D-${daysLeft}`}</span>
}
export function RoleAvailability({ role }: { role: Pick<RoleSummary, 'roleName' | 'remaining'> }) {
  return <div className="post-role-line"><span>{role.roleName}</span><span className="post-chip post-chip-blue">남은 {role.remaining}명</span></div>
}
export function PostCard({ post, index, search }: { post: PostSummary; index: number; search: string }) {
  return <article className="post-card">
    <Link to={`/posts/${post.id}${search}`} className="post-card-link">
      <div className={`post-card-cover post-tint-${index % 4}`}><span className="post-chip">{categoryLabels[post.category]}</span><StatusBadge status={post.status} daysLeft={post.dDay} /></div>
      <div className="post-card-body"><h2>{post.title}</h2>{post.roles.map((role, roleIndex) => <RoleAvailability key={`${role.roleCode}-${roleIndex}`} role={role} />)}<p className="post-card-meta">{[post.leader.schoolName, meetingLabels[post.meetingType]].filter(Boolean).join(' · ')}</p></div>
    </Link>
  </article>
}
export function PostError({ message, retry }: { message: string; retry: () => void }) {
  return <div className="post-message" role="alert"><p>{message}</p><Button onClick={retry}>다시 시도</Button></div>
}
export function MockControls({ scenario, onScenario, viewer, onViewer }: {
  scenario: Scenario; onScenario: (value: Scenario) => void; viewer?: Viewer; onViewer?: (value: Viewer) => void
}) {
  if (!isPostMock) return null
  return <aside className="post-dev" aria-label="개발용 예시 데이터 설정"><strong>개발용 예시 데이터 · 실제 인증 및 지원 상태가 아닙니다</strong><div>
    <label>화면 상태 <select value={scenario} onChange={e => onScenario(e.target.value as Scenario)}><option value="normal">정상</option><option value="slow">느린 로딩</option>{!onViewer && <><option value="empty">빈 목록</option><option value="moreError">추가 로딩 오류</option></>}<option value="error">오류</option></select></label>
    {onViewer && <label>예시 사용자 <select value={viewer} onChange={e => onViewer(e.target.value as Viewer)}><option value="guest">비로그인</option><option value="other">다른 사람</option><option value="owner">작성자 본인</option><option value="applied">지원 대기</option><option value="accepted">지원 수락</option><option value="rejected">지원 거절</option><option value="canceled">지원 취소</option><option value="applicationClosed">지원 마감</option><option value="left">팀에서 나감</option></select></label>}
  </div></aside>
}
