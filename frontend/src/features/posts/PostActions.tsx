import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../../components/Button'
import type { PostDetail, Viewer } from './model'
import { closePost, getPostContact } from './post-actions-api'
import { canClosePost, canReadContact, createPostActions, type PostActionsState } from './post-actions-controller'
import { createMockPostActions } from './post-actions-mock'
import { isPostMock, loadPost, type Scenario } from './source'
import './post-actions.css'

type Props = {
  post: PostDetail; onPost: (post: PostDetail) => void; viewer: Viewer; scenario: Scenario;
  closing: string | null; contact: string | null;
  // 공통 인증 연동용 주입 지점. 인증 정보를 저장하지 않으며 세션 변경 시 자식을 교체합니다.
  session?: { userId: string; token: string }
}
export function PostActions(props: Props) {
  const { post, session } = props
  const scope = JSON.stringify([post.id, session?.userId, session?.token, post.isMine, post.myApplication?.status,
    isPostMock && props.viewer, isPostMock && props.closing, isPostMock && props.contact])
  // key changes remove old contact during render, before effect cleanup/async completion.
  return <ScopedPostActions key={scope} {...props} />
}
function ScopedPostActions({ post, onPost, session, scenario, viewer, closing, contact }: Props) {
  const mounted = useRef(false)
  const focusFrame = useRef<number | null>(null)
  const [actions] = useState(() => createPostActions(post, {
    ...(isPostMock ? createMockPostActions(post, scenario, viewer, closing, contact) : {
      close: (signal: AbortSignal) => closePost(post.id, { token: session?.token, signal }),
      contact: (signal: AbortSignal) => getPostContact(post.id, { token: session?.token, signal }),
      load: (signal: AbortSignal) => loadPost(String(post.id), scenario, signal, viewer, { token: session?.token }),
    }), onPost,
  }))
  const state = useSyncExternalStore(actions.subscribe, actions.getSnapshot, actions.getSnapshot)
  const [confirm, setConfirm] = useState(false)
  useEffect(() => {
    mounted.current = true; actions.activate()
    return () => { mounted.current = false; actions.dispose(); if (focusFrame.current !== null) cancelAnimationFrame(focusFrame.current) }
  }, [actions])
  useEffect(() => { actions.updatePost(post) }, [actions, post])
  const submit = async () => {
    await actions.close()
    if (mounted.current && actions.getSnapshot().closeConfirmed) {
      setConfirm(false)
      focusFrame.current = requestAnimationFrame(() => { if (mounted.current) document.querySelector<HTMLElement>('[data-close-notice]')?.focus() })
    }
  }
  return <>
    <PostActionsView state={state} mock={isPostMock} authenticated={!!session?.token?.trim()} onConfirm={() => setConfirm(true)}
      onRefresh={() => void actions.refresh()} onContact={() => void actions.readContact()} onHide={actions.clearContact}
      onCopy={() => void actions.copy(text => navigator.clipboard.writeText(text))} />
    {confirm && <ClosePostDialog state={state} onClose={() => setConfirm(false)} onSubmit={() => void submit()} />}
  </>
}

export function PostActionsView({ state, mock, authenticated, onConfirm, onRefresh, onContact, onHide, onCopy }: {
  state: PostActionsState; mock: boolean; authenticated: boolean; onConfirm: () => void; onRefresh: () => void;
  onContact: () => void; onHide: () => void; onCopy: () => void;
}) {
  const eligible = canReadContact(state.post)
  return <div className="post-actions">
    {state.post.isMine && <Button disabled={!canClosePost(state.post) || state.closing || state.refreshing || state.needsRefresh} onClick={onConfirm}>
      {state.closing ? '마감 처리 중…' : state.post.status === 'CLOSED' ? '모집 마감됨' : '모집 마감하기'}
    </Button>}
    {state.closeNotice && <p className="post-note" role="status" tabIndex={-1} data-close-notice>{mock && '개발용 예시 · '}{state.closeNotice}</p>}
    {state.closeError && <p className="field-error" role="alert">{state.closeError}</p>}
    {state.needsRefresh && <div role="status"><p>{state.refreshing ? '최신 상세·모집 현황을 확인하고 있어요.' : state.closeConfirmed ? '마감 상태는 확인됐지만 최신 상세·모집 현황을 불러오지 못했어요. 다시 마감할 필요는 없습니다.' : '최신 상태 확인 전에는 마감 요청을 다시 보내지 않습니다.'}</p>
      <Button disabled={state.refreshing || state.closing} onClick={onRefresh}>{state.refreshing ? '상세 갱신 중…' : '상세 정보만 다시 불러오기'}</Button></div>}
    <section className="post-panel post-contact" aria-label="연락 수단">
      <h2><img src={`${import.meta.env.BASE_URL}posts/lock.svg`} width="18" height="18" alt="" />연락 수단</h2>
      <p className="post-note">팀장과 수락된 지원자만 확인할 수 있어요. 모집 마감 후에도 열람할 수 있습니다.</p>
      {eligible ? <>
        <Button onClick={onContact} disabled={state.contactLoading}>{state.contactLoading ? '연락 수단 확인 중…' : state.contact ? '연락 수단 다시 확인' : state.contactError ? '연락 수단 다시 시도' : '연락 수단 확인'}</Button>
        {state.contactLoading && <p role="status">조회 권한과 연락 수단을 확인하고 있어요.</p>}
        {state.contactError && <p className="field-error" role="alert">{state.contactError}</p>}
        {state.contact && <div className="post-contact-result">
          <a href={state.contact} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">{state.contact} ↗ (새 창)</a>
          <div className="post-secondary-actions"><Button onClick={onCopy} disabled={state.copying}>{state.copying ? '복사 중…' : '연락 수단 복사'}</Button><Button onClick={onHide}>연락 수단 숨기기</Button></div>
        </div>}
        {state.copyNotice && <p role="status">{state.copyNotice}</p>}
      </> : <p className="post-note">수락 전이거나 지원이 거절·취소·마감되었거나 팀에서 나간 경우에는 조회할 수 없어요.</p>}
      {!mock && !authenticated && <p className="post-note">공통 인증 연동 대기 중 · 인증 연동 전에는 마감·연락 수단 조회 요청을 보내지 않습니다.</p>}
      {mock && <p className="post-note">개발용 예시 · 실제 요청·DB 변경이 없으며 연락 수단은 가상 주소입니다.</p>}
    </section>
  </div>
}

function ClosePostDialog({ state, onClose, onSubmit }: { state: PostActionsState; onClose: () => void; onSubmit: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const locked = state.closing || state.refreshing
  useEffect(() => {
    const element = dialog.current!
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'; element.showModal()
    return () => { element.close(); document.body.style.overflow = overflow; if (trigger?.isConnected && !trigger.matches(':disabled')) trigger.focus() }
  }, [])
  return createPortal(<dialog ref={dialog} className="post-close-dialog" aria-labelledby="post-close-title" aria-describedby="post-close-impact"
    onCancel={event => { event.preventDefault(); if (!locked) onClose() }}>
    <form onSubmit={event => { event.preventDefault(); onSubmit() }} aria-busy={locked}>
      <h2 id="post-close-title">모집을 마감할까요?</h2>
      <p id="post-close-impact">글과 모든 역할의 모집이 마감됩니다. 대기 중인 지원은 마감 상태로 변경되고 대기자에게 알림이 발송됩니다. 이미 수락·거절·취소된 지원 상태는 유지됩니다.</p>
      <p>수락된 지원자와 팀장은 마감 후에도 연락 수단을 확인할 수 있어요. 이 화면에서는 다시 모집을 여는 기능을 제공하지 않습니다.</p>
      {state.closeError && <p className="field-error" role="alert">{state.closeError}</p>}
      {locked && <p role="status">처리 결과를 확인하고 있어요. 잠시 기다려 주세요.</p>}
      <div className="post-secondary-actions"><Button autoFocus disabled={locked} onClick={onClose}>취소</Button><Button type="submit" disabled={locked || state.needsRefresh || !canClosePost(state.post)}>모집 마감 확정</Button></div>
    </form>
  </dialog>, document.body)
}
