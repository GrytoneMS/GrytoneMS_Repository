import { useEffect, useRef, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { isPostMock } from '../features/posts/source'
import { createManagementController, initialManagementState } from '../features/applicants/controller'
import { managementService } from '../features/applicants/source'
import { ApplicantsView } from '../features/applicants/ApplicantsView'
import { DecisionDialog } from '../features/applicants/DecisionDialog'
import { findApplication, type Decision, type Reason } from '../features/applicants/model'
import '../features/applicants/applicants.css'

export default function PostApplicantsPage() {
  const { postId = '' } = useParams()
  const [params] = useSearchParams()
  return <ApplicantsContent key={`${postId}:${isPostMock ? params.toString() : 'real'}`} postId={postId} />
}
function ApplicantsContent({ postId }: { postId: string }) {
  const [params, setParams] = useSearchParams()
  const [state, setState] = useState(initialManagementState)
  const [reason, setReason] = useState<Reason | null>(null)
  const [confirmation, setConfirmation] = useState<{ id: number; decision: Decision } | null>(null)
  const controller = useRef<ReturnType<typeof createManagementController> | null>(null)
  const resultNotice = useRef<HTMLParagraphElement>(null)
  const errorNotice = useRef<HTMLDivElement>(null)
  const query = isPostMock ? params.toString() : ''
  useEffect(() => {
    // 공통 인증 컨텍스트가 아직 없습니다. 임시 토큰이나 저장 정책을 만들지 않습니다.
    const manager = createManagementController(managementService(postId, new URLSearchParams(query)), setState)
    controller.current = manager
    void manager.load()
    return () => manager.dispose()
  }, [postId, query])
  useEffect(() => { if (state.notice) resultNotice.current?.focus() }, [state.revision, state.notice])
  useEffect(() => { if (state.error) errorNotice.current?.focus() }, [state.error])
  const target = confirmation && state.snapshot ? findApplication(state.snapshot, confirmation.id) : null
  const search = isPostMock ? `?${params}` : ''
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }, { replace: true })
  return <section className="applicants-page" aria-label="모집글 지원자 관리" aria-busy={state.busy || state.loading}>
    <h1 className="applicants-page-title">모집글 지원자 관리</h1>
    {isPostMock && <aside className="post-dev" aria-label="지원자 관리 개발 예시"><strong>개발용 예시 · 실제 인증·지원자·처리 요청이 아닙니다</strong><div>
      <label>사용자<select value={params.get('viewer') ?? 'owner'} disabled={state.busy} onChange={event => update('viewer', event.target.value)}><option value="owner">팀장</option><option value="other">다른 사용자</option><option value="guest">비로그인</option></select></label>
      <label>조회 상태<select value={params.get('manage') ?? 'normal'} disabled={state.busy} onChange={event => update('manage', event.target.value)}>{Object.entries({ normal: '여러 역할·지원 상태', empty: '지원자 없음', slow: '느린 조회', error: '조회 실패', notFound: '없는 모집글', full: '역할 마감·정원 도달', closedPost: '글 마감·역할 열림', canceled: '취소 상태 방어 표시' }).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>처리 결과<select value={params.get('result') ?? 'success'} disabled={state.busy} onChange={event => update('result', event.target.value)}>{Object.entries({ success: '처리 성공', failure: '처리 실패', refreshFailure: '성공 후 재조회 실패', full: '정원 초과 오류', processed: '이미 처리됨', expired: '인증 만료', forbidden: '권한 없음', notFound: '지원 없음', network: '네트워크 응답 실패' }).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    </div><p>조건 변경·새로고침 시 예시 처리는 초기화됩니다. 취소 지원은 현재 서버 조회에서 제외됩니다.</p></aside>}
    {state.notice && <p className="post-safety" role="status" tabIndex={-1} ref={resultNotice}>{state.notice}{isPostMock && ' (개발 예시이며 실제 변경 없음)'}</p>}
    {state.busy && !state.notice && <p className="post-message" role="status">처리 요청을 보내는 중이에요. 결과 확인까지 기다려 주세요.</p>}
    {state.error && <div className="post-message" role="alert" tabIndex={-1} ref={errorNotice}><p>{state.error.message}</p><Button disabled={state.busy || state.loading} onClick={() => void controller.current?.load()}>조회 다시 시도</Button></div>}
    {!state.snapshot && !state.error && <p className="post-message" role="status">지원자 목록과 모집 현황을 불러오는 중…</p>}
    {!state.snapshot && <Link to={`/posts/${postId}${search}`}>모집글 상세로 돌아가기</Link>}
    {state.snapshot && <ApplicantsView snapshot={state.snapshot} state={state} reason={reason} onReason={setReason} onAction={(id, decision) => setConfirmation({ id, decision })} onRefresh={() => void controller.current?.load()} search={search} />}
    {confirmation && target && <DecisionDialog name={target.application.applicant.nickname ?? '이름 미등록'} roleName={target.role.roleName} decision={confirmation.decision} reason={reason}
      onCancel={() => setConfirmation(null)} onConfirm={() => { setConfirmation(null); void controller.current?.decide(confirmation.id, confirmation.decision, reason) }} />}
  </section>
}
