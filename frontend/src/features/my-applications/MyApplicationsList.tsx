import { Link } from 'react-router'
import { Button } from '../../components/Button'
import type { ListState } from './controller'
import { actionFor, applicationTitle, detailPath, formatTimestamp, knownStatus, statusLabel, type Action, type MyApplication } from './model'

export function MyApplicationsList({ state, mock, restrictedExample = false, onAction }: { state: ListState; mock: boolean; restrictedExample?: boolean; onAction: (item: MyApplication, action: Action) => void }) {
  const locked = state.loading || state.moreLoading || state.busyId !== null || state.needsRefresh
  return <div className="my-applications-list">{state.items.map(item => {
    const action = actionFor(item), path = detailPath(item, mock)
    return <article className={`post-panel my-application-card${item.post.deleted ? ' my-application-deleted' : ''}`} key={item.id}>
      <div className="my-application-heading"><div><h2>{path ? <Link to={path}>{applicationTitle(item)}</Link> : applicationTitle(item)}</h2><p>지원 역할: {item.roleName}</p></div>
        <span className={`post-chip my-application-status ${knownStatus(item.status) ? item.status.toLowerCase() : 'unknown'}`}>{statusLabel(item.status)}</span>
      </div>
      <div className="my-application-dates"><span>지원일 <time dateTime={item.createdAt}>{formatTimestamp(item.createdAt)}</time></span>{item.decidedAt && <span>처리일 <time dateTime={item.decidedAt}>{formatTimestamp(item.decidedAt)}</time></span>}</div>
      {item.status === 'CLOSED' && <p className="my-application-caption">이 지원이 마감 처리됐어요.</p>}
      {!knownStatus(item.status) && <p className="my-application-caption">새로운 지원 상태입니다. 이 화면에서는 취소·팀 나가기 요청을 보내지 않습니다.</p>}
      {item.post.deleted && <p className="my-application-caption">삭제된 모집글은 상세를 열 수 없어요. 지원 상태는 위에 표시된 결과를 유지합니다.</p>}
      {mock && restrictedExample && item.post.id === 12 && <p className="my-application-caption">개발 예시: 모집글은 마감됐지만 지원은 수락 상태예요.</p>}
      {item.status === 'ACCEPTED' && path && <div className="my-application-contact"><h3>연락 수단 (수락된 지원자에게만 공개)</h3><Link to={path}>모집글 상세에서 연락 수단 확인 →</Link><p>안전 수칙: 첫 모임은 공개된 장소에서, 개인 연락처 공유는 신중하게.</p></div>}
      {action && <Button className={action === 'cancel' ? 'my-application-soft' : 'my-application-outline'} disabled={locked} onClick={() => onAction(item, action)}>{state.busyId === item.id ? '처리 중…' : action === 'cancel' ? '지원 취소' : '팀 나가기'}</Button>}
    </article>
  })}</div>
}
