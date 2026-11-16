import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { ApplicationActionDialog } from '../features/my-applications/ApplicationActionDialog'
import { MyApplicationsList } from '../features/my-applications/MyApplicationsList'
import { createMyApplicationsController, initialListState } from '../features/my-applications/controller'
import { actionFor, filterParams, readFilter, statusLabels, type Action, type MyApplication, type Session } from '../features/my-applications/model'
import { actionExamples, exampleMode, listExamples, myApplicationsService } from '../features/my-applications/source'
import { isPostMock } from '../features/posts/source'
import '../features/my-applications/my-applications.css'

export default function MyApplicationsPage({ session }: { session?: Session } = {}) {
  const [params] = useSearchParams()
  const status = readFilter(params), mock = exampleMode(params)
  const exampleQuery = mock ? params.toString() : ''
  // 사용자/토큰/필터 교체 때 이전 목록·확인창을 즉시 폐기합니다. 토큰은 저장하거나 URL에 넣지 않습니다.
  const scope = JSON.stringify([session?.userId, session?.token, status, exampleQuery])
  return <MyApplicationsContent key={scope} session={session} />
}
function MyApplicationsContent({ session }: { session?: Session }) {
  const [params, setParams] = useSearchParams()
  const status = readFilter(params), mock = exampleMode(params), query = mock ? params.toString() : ''
  const userId = session?.userId, token = session?.token
  const [state, setState] = useState(initialListState)
  const [confirmation, setConfirmation] = useState<{ item: MyApplication; action: Action } | null>(null)
  const controller = useRef<ReturnType<typeof createMyApplicationsController> | null>(null)
  const notice = useRef<HTMLParagraphElement>(null), error = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const manager = createMyApplicationsController(myApplicationsService(new URLSearchParams(query), userId !== undefined && token !== undefined ? { userId, token } : undefined), status, setState)
    controller.current = manager
    void manager.load()
    const refresh = () => { void manager.load() }
    const visible = () => { if (document.visibilityState === 'visible') refresh() }
    window.addEventListener('focus', refresh); window.addEventListener('pageshow', refresh)
    document.addEventListener('visibilitychange', visible)
    return () => { manager.dispose(); window.removeEventListener('focus', refresh); window.removeEventListener('pageshow', refresh); document.removeEventListener('visibilitychange', visible) }
  }, [status, query, userId, token])
  useEffect(() => { if (state.notice) notice.current?.focus() }, [state.notice])
  useEffect(() => { if (state.error) error.current?.focus() }, [state.error])
  const locked = state.loading || state.moreLoading || state.busyId !== null
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }, { replace: true })
  const validConfirmation = confirmation && !locked && !state.needsRefresh && state.items.some(item => item.id === confirmation.item.id && actionFor(item) === confirmation.action)
  return <section className="my-applications-page" aria-label="내 지원 내역" aria-busy={locked}>
    <h1>내 지원 내역</h1>
    {isPostMock && <aside className="post-dev" aria-label="내 지원 내역 개발 예시"><strong>{mock ? '개발용 예시 · 실제 조회·취소·팀 나가기 요청이 아닙니다' : '실제 API 모드 · 공통 인증 연결 필요'}</strong><div>
      <label>데이터<select disabled={state.busyId !== null} value={mock ? 'true' : 'false'} onChange={event => update('example', event.target.value)}><option value="false">실제 API</option><option value="true">개발 예시</option></select></label>
      {mock && <><label>사용자<select disabled={state.busyId !== null} value={params.get('viewer') ?? 'alice'} onChange={event => update('viewer', event.target.value)}><option value="alice">사용자 A</option><option value="bob">사용자 B</option><option value="guest">로그아웃</option></select></label>
        <label>조회<select disabled={state.busyId !== null} value={params.get('list') ?? 'normal'} onChange={event => update('list', event.target.value)}>{Object.entries(listExamples).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label>처리 결과<select disabled={state.busyId !== null} value={params.get('result') ?? 'success'} onChange={event => update('result', event.target.value)}>{Object.entries(actionExamples).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></>}
    </div>{mock && <><p>예시 처리 결과는 같은 사용자·시나리오의 필터 이동에도 유지되며 새로고침 시 초기화됩니다. 목록의 링크는 예시 모집글 상세로 연결됩니다.</p><Button disabled={state.busyId !== null} onClick={() => update('reset', String(Date.now()))}>예시 초기화</Button></>}</aside>}
    {!mock && !session?.token?.trim() && <p className="post-safety">공통 인증 연동 대기 중 · 로그인/토큰 저장 기능을 별도로 만들지 않았습니다. 실제 요청은 보내지 않습니다.</p>}
    <nav className="my-application-filters" aria-label="지원 상태 필터"><Button disabled={state.busyId !== null} aria-pressed={status === ''} onClick={() => setParams(previous => filterParams(previous, ''))}>전체</Button>{Object.entries(statusLabels).map(([value, label]) => <Button key={value} disabled={state.busyId !== null} aria-pressed={status === value} onClick={() => setParams(previous => filterParams(previous, value as keyof typeof statusLabels))}>{label}</Button>)}</nav>
    <p className="my-application-caption">최신 지원순 · 지원 상태와 모집글의 모집 상태는 서로 달라요.</p>
    {state.notice && <p className="post-safety" role="status" tabIndex={-1} ref={notice}>{state.notice}{mock && ' (개발 예시)'}</p>}
    {state.error && <div className="post-message" role="alert" tabIndex={-1} ref={error}><p>{state.error.message}</p><Button disabled={locked} onClick={() => void controller.current?.load()}>목록 다시 조회</Button></div>}
    {state.needsRefresh && !state.error && <p role="status">처리 결과를 최신 목록에서 확인하고 있어요. 확인 전에는 추가 처리할 수 없습니다.</p>}
    {state.loading && <p className="post-message" role="status">{state.loaded ? '최신 지원 내역을 확인하는 중…' : '지원 내역을 불러오는 중…'}</p>}
    {state.items.length > 0 && <MyApplicationsList state={state} mock={mock} restrictedExample={params.get('list') === 'restrictedPosts'} onAction={(item, action) => setConfirmation({ item, action })} />}
    {state.loaded && !state.loading && !state.error && !state.items.length && <div className="post-message"><h2>{status ? `${statusLabels[status]} 상태의 지원이 없어요` : '아직 지원 내역이 없어요'}</h2><p>다른 상태를 선택하거나 관심 있는 모집글을 살펴보세요.</p></div>}
    {state.moreError && <p role="alert">추가 목록 조회에 실패했어요. {state.moreError}</p>}
    {state.hasNext && <Button className="my-application-more" disabled={locked || state.needsRefresh} onClick={() => void controller.current?.more()}>{state.moreLoading ? '더 불러오는 중…' : state.moreError ? '더 보기 다시 시도' : '더 보기'}</Button>}
    {state.loaded && !state.error && <Button className="my-application-refresh" disabled={locked} onClick={() => void controller.current?.load()}>최신 상태 새로고침</Button>}
    {validConfirmation && <ApplicationActionDialog item={confirmation.item} action={confirmation.action} onClose={() => setConfirmation(null)} onConfirm={() => { const selected = confirmation; setConfirmation(null); void controller.current?.act(selected.item.id, selected.action) }} />}
  </section>
}
