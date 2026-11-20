import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { createPortal } from 'react-dom'
import { Button } from '../components/Button'
import { ClosePostDialog } from '../features/posts/PostActions'
import { isPostMock } from '../features/posts/source'
import { canDelete, deletionNote, type MyPost } from '../features/my-posts/api'
import { createMyPosts, type MyState, type MyService } from '../features/my-posts/controller'
import type { PostStatus } from '../features/posts/model'
import { myPostsService } from '../features/my-posts/source'
import '../features/my-posts/my-posts.css'
import { AuthStatus } from '../features/auth/AuthContext'
import { useAuth } from '../features/auth/useAuth'

export default function MyPostsPage() {
  const auth = useAuth(), token = auth.session?.token, store = auth.store
  const [params] = useSearchParams()
  const status = params.get('status') === 'CLOSED' ? 'CLOSED' : 'OPEN'
  const query = isPostMock ? new URLSearchParams({ mine: params.get('mine') ?? 'normal', result: params.get('result') ?? 'success' }).toString() : ''
  const service = useMemo<MyService>(() => {
    const base = myPostsService(new URLSearchParams(query), { token })
    return isPostMock ? base : {
      load: (status, cursor, signal) => store.execute((_, authSignal) => base.load(status, cursor, AbortSignal.any([signal, authSignal]))),
      close: (id, signal) => store.execute((_, authSignal) => base.close(id, AbortSignal.any([signal, authSignal]))),
      remove: (id, signal) => store.execute((_, authSignal) => base.remove(id, AbortSignal.any([signal, authSignal]))),
    }
  }, [query, token, store])
  if (!isPostMock && auth.status !== 'authenticated') return <section className="post-message"><h1>내 모집글</h1><AuthStatus /></section>
  return <MyPostsContent key={`${auth.scope}:${status}:${query}`} status={status} service={service} />
}
function MyPostsContent({ status, service }: { status: PostStatus; service: MyService }) {
  const [params, setParams] = useSearchParams()
  const manager = useRef<ReturnType<typeof createMyPosts> | null>(null)
  const [state, setState] = useState<MyState | null>(null)
  const [confirmation, setConfirmation] = useState<{ post: MyPost; kind: 'close' | 'delete' } | null>(null)
  const notice = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    const controller = createMyPosts(service, status)
    manager.current = controller
    const unsubscribe = controller.subscribe(() => setState(controller.getSnapshot()))
    void controller.load()
    return () => { unsubscribe(); controller.dispose() }
  }, [service, status])
  useEffect(() => { if (state?.notice) notice.current?.focus() }, [state?.notice])
  const update = (key: string, value: string) => { setConfirmation(null); setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }) }
  const locked = !!state && (state.busy !== null || state.loading || state.needsRefresh)
  const busy = !!state && state.busy !== null
  const submit = async () => {
    if (!confirmation) return
    const controller = manager.current!
    await controller.act(confirmation.post.id, confirmation.kind)
    if (manager.current === controller && controller.getSnapshot().notice) setConfirmation(null)
  }
  const search = isPostMock ? '?viewer=owner' : ''
  return <section className="my-posts" aria-label="내 모집글">
    <h1>내 모집글</h1>
    {isPostMock && <aside className="post-dev"><strong>개발용 예시 · 실제 API·DB 변경 없음</strong><div>
      <label>조회 예시<select disabled={busy} value={params.get('mine') ?? 'normal'} onChange={e => update('mine', e.target.value)}>{Object.entries({ normal: '정상 · 여러 역할·지원자 있음/없음', empty: '빈 목록', error: '조회 실패', slow: '느린 조회', moreError: '더 보기 실패' }).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>처리 예시<select disabled={busy} value={params.get('result') ?? 'success'} onChange={e => update('result', e.target.value)}>{Object.entries({ success: '성공', failure: '실패', network: '응답 불명확', refreshFailure: '성공 후 재조회 실패', hasApplicants: '삭제 시 지원자 존재 오류' }).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</select></label>
    </div></aside>}
    <nav className="my-post-tabs" aria-label="내 모집글 상태">{(['OPEN','CLOSED'] as const).map(tab => <Button key={tab} aria-pressed={status === tab} disabled={busy} onClick={() => update('status', tab)}>{tab === 'OPEN' ? '모집중' : '마감'}</Button>)}</nav>
    <p className="my-edit-note">수정 기능은 준비 중입니다. 이번 화면에는 수정 화면이 아직 연결되지 않았어요.</p>
    {!isPostMock && <p className="post-note">실제 로그인 사용자 기준으로 내 모집글을 조회합니다.</p>}
    {state?.notice && <p role="status" tabIndex={-1} ref={notice} className="post-safety">{isPostMock && '개발용 예시 · '}{state.notice}</p>}
    {state?.error && <div role="alert" className="post-message"><p>{state.error}</p><Button disabled={busy || state.loading} onClick={() => { setConfirmation(null); void manager.current?.load() }}>목록 다시 조회</Button></div>}
    {(!state || state.loading || state.status !== status) && <p role="status" className="post-message">내 모집글을 불러오는 중…</p>}
    {state && !state.loading && !state.error && !state.items.length && <div className="post-message"><p>{status === 'OPEN' ? '모집중인 글이 없어요.' : '마감된 글이 없어요.'}</p><Link to="/posts/new">모집글 작성</Link></div>}
    <div className="my-post-list" aria-busy={!!state?.loading || busy}>{state?.status === status && state.items.map(post => <MyPostCard key={post.id} post={post} search={search} locked={locked} busy={state.busy === post.id} onAction={kind => setConfirmation({ post, kind })} />)}</div>
    {state?.moreError && <p role="alert" className="field-error">{state.moreError}</p>}
    {state?.hasNext && <div className="my-more"><Button disabled={locked || state.more} onClick={() => void manager.current?.load(true)}>{state.more ? '더 불러오는 중…' : state.moreError ? '더 보기 다시 시도' : '더 보기'}</Button></div>}
    {confirmation?.kind === 'close' && <ClosePostDialog state={{ post: { isMine: true, status: state?.items.find(post => post.id === confirmation.post.id)?.status ?? 'CLOSED' }, closing: busy, refreshing: !!state?.loading, needsRefresh: !!state?.needsRefresh, closeError: state?.error ?? '' }} onClose={() => setConfirmation(null)} onSubmit={() => void submit()} />}
    {confirmation?.kind === 'delete' && <DeleteDialog title={confirmation.post.title} locked={busy || !!state?.loading} blocked={!!state?.needsRefresh} error={state?.error ?? ''} onClose={() => setConfirmation(null)} onSubmit={() => void submit()} />}
  </section>
}

export function MyPostCard({ post, search, locked, busy, onAction }: { post: MyPost; search: string; locked: boolean; busy: boolean; onAction: (kind: 'close' | 'delete') => void }) {
  const badge = post.status === 'CLOSED' ? '모집 마감' : post.dDay === 0 ? '모집 D-Day' : post.dDay < 0 ? `마감일 ${Math.abs(post.dDay)}일 경과` : `모집 D-${post.dDay}`
  return <article className="my-post-card">
    <div className="my-post-heading"><h2><Link to={`/posts/${post.id}${search}`}>{post.title}</Link></h2><span className={`my-deadline ${post.status === 'CLOSED' ? 'closed' : post.dDay <= 3 ? 'urgent' : ''}`}>{badge}</span></div>
    <div className="my-role-list">{post.roles.map(role => <div className="my-role-row" key={role.id}><strong>{role.roleName}</strong><div><span className="my-applicant-count">지원 {role.applicantCount}</span><span className="my-filled-count">수락 {role.filledCount}/{role.headcount}</span></div></div>)}</div>
    <footer className="my-post-footer">{!canDelete(post) && <p>{deletionNote(post)}</p>}<div className="my-card-actions">
      <Link className="button" to={`/posts/${post.id}/applicants${search}`}>지원자 관리</Link>
      <Button disabled title="모집글 수정 화면 미구현">수정 · 준비 중</Button>
      <Button className="my-soft-button" disabled={locked || busy || post.status === 'CLOSED'} onClick={() => onAction('close')}>{busy ? '처리 중…' : post.status === 'CLOSED' ? '모집 마감됨' : '수동 마감'}</Button>
      {canDelete(post) && <Button className="my-delete-button" disabled={locked || busy} onClick={() => onAction('delete')}>삭제</Button>}
    </div></footer>
  </article>
}

function DeleteDialog({ title, locked, blocked, error, onClose, onSubmit }: { title: string; locked: boolean; blocked: boolean; error: string; onClose: () => void; onSubmit: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const element = dialog.current!, trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null, overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'; element.showModal()
    return () => { element.close(); document.body.style.overflow = overflow; if (trigger?.isConnected && !trigger.matches(':disabled')) trigger.focus() }
  }, [])
  return createPortal(<dialog ref={dialog} className="post-close-dialog" aria-labelledby="my-delete-title" onCancel={e => { e.preventDefault(); if (!locked) onClose() }}><form onSubmit={e => { e.preventDefault(); onSubmit() }} aria-busy={locked}>
    <h2 id="my-delete-title">모집글을 삭제할까요?</h2><p>{title}</p><p>삭제하면 목록에서 사라집니다. 취소한 지원을 제외한 지원자가 없을 때만 삭제할 수 있으며 서버에서 다시 확인합니다.</p>
    {error && <p role="alert" className="field-error">{error}</p>}
    <div className="post-secondary-actions"><Button autoFocus disabled={locked} onClick={onClose}>취소</Button><Button type="submit" disabled={locked || blocked}>{locked ? '처리 중…' : '삭제 확정'}</Button></div>
  </form></dialog>, document.body)
}
