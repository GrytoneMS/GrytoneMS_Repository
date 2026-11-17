import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import PostCreatePage from './PostCreatePage'
import { Button } from '../components/Button'
import { editDraft, editFailure, loadEditSnapshot, roleRules, toEditRequest, updatePost, type EditSnapshot } from '../features/posts/edit-model'
import { editExamples, loadEditExample, saveEditExample } from '../features/posts/edit-examples'
import { isPostMock } from '../features/posts/source'
import { createEditSubmitter } from '../features/posts/edit-submit'
import type { CreateErrors, PostDraft } from '../features/posts/create-model'
import { AuthStatus } from '../features/auth/AuthContext'
import { useAuth } from '../features/auth/useAuth'

export default function PostEditPage() {
  const { postId = '' } = useParams()
  const [params] = useSearchParams()
  const example = isPostMock && Object.hasOwn(editExamples, params.get('editExample') ?? '') ? params.get('editExample')! : ''
  return <EditContent key={`${postId}:${example}`} id={postId} example={example} />
}
function EditContent({ id, example }: { id: string; example: string }) {
  const auth = useAuth(), store = auth.store
  const navigate = useNavigate()
  const [snapshot, setSnapshot] = useState<EditSnapshot | null>(null)
  const [loadFailure, setLoadFailure] = useState('')
  const [retry, setRetry] = useState(0)
  const [errors, setErrors] = useState<CreateErrors>({})
  const [failure, setFailure] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const dirty = useRef(false)
  const hasSnapshot = useRef(false)
  const submitter = useMemo(() => snapshot ? createEditSubmitter(snapshot, (draft, signal) =>
    example && example !== 'noAuth' ? saveEditExample(draft, snapshot, example, signal)
      : store.execute((session, authSignal) => updatePost(snapshot.post.id, toEditRequest(draft, snapshot), { token: session.token, signal: AbortSignal.any([signal, authSignal]) }))) : null, [snapshot, example, store])
  useEffect(() => () => submitter?.cancel(), [submitter])
  const detail = `/posts/${id}${example ? '?viewer=owner' : ''}`
  useEffect(() => {
    if (!example && (auth.status !== 'authenticated' || hasSnapshot.current)) return
    const controller = new AbortController()
    const loading = example ? loadEditExample(id, example) : store.execute((session, authSignal) => loadEditSnapshot(id, { token: session.token, signal: AbortSignal.any([controller.signal, authSignal]) }))
    loading.then(value => { if (!controller.signal.aborted) { hasSnapshot.current = true; setSnapshot(value) } })
      .catch((error: unknown) => { if (!controller.signal.aborted) setLoadFailure(editFailure(error, 'load').message) })
    return () => controller.abort()
  }, [id, example, retry, auth.status, store])
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty.current) { event.preventDefault(); event.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [])
  const changed = useCallback((draft: PostDraft) => {
    dirty.current = !!snapshot && JSON.stringify(draft) !== JSON.stringify(editDraft(snapshot))
    setErrors({}); setFailure('')
  }, [snapshot])
  function focusErrors() {
    requestAnimationFrame(() => {
      const target = document.querySelector<HTMLElement>('.post-create [aria-invalid="true"]:not(:disabled)') ?? document.querySelector<HTMLElement>('.create-error-summary')
      target?.focus(); target?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    })
  }
  async function submit(draft: PostDraft) {
    await submitter?.submit(draft, {
      invalid: next => { setErrors(next); setFailure(''); focusErrors() },
      start: () => { setSubmitting(true); setErrors({}); setFailure('') },
      success: () => {
        dirty.current = false
        navigate(example === 'refreshFailure' ? `${detail}&scenario=error` : detail, { state: { postEditSaved: true, example: !!example } })
      },
      failure: result => { setErrors(result.fields); setFailure(result.message); focusErrors() },
      settled: () => setSubmitting(false),
    })
  }
  const examples = isPostMock && <div className="application-examples"><label>개발용 수정 예시<select value={example} onChange={event => navigate(event.target.value ? `/posts/${id}/edit?editExample=${event.target.value}` : `/posts/${id}/edit`)}>
    <option value="">실제 API</option>{Object.entries(editExamples).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
  </select></label><p>예시 모드는 실제 API·DB에 저장하지 않습니다. 정상 예시 저장은 현재 개발 서버 메모리에서 상세 조회에 반영됩니다.</p></div>
  return <section aria-label="모집글 수정">
    {examples}
    {!example && <AuthStatus />}
    {!snapshot ? !example && auth.status !== 'authenticated' ? null : <div className="post-message">{loadFailure ? <><h1>모집글 수정</h1><p role="alert">{loadFailure}</p><Button onClick={() => { setLoadFailure(''); setRetry(value => value + 1) }}>초기 정보 다시 불러오기</Button><Link to={detail}>상세로 돌아가기</Link></> : <p role="status">수정할 모집글과 연락처·지원 현황을 불러오는 중…</p>}</div> : <PostCreatePage editor={{ initial: editDraft(snapshot), rules: roleRules(snapshot), errors, failure, submitting, onChange: changed, submit,
      cancel: () => { dirty.current = false; navigate(detail) },
      notice: <div className="post-safety"><p>{example ? '개발용 예시입니다. 실제 모집글은 수정되지 않습니다.' : '로그인한 본인의 글을 수정합니다. 오류가 나도 입력은 유지됩니다.'}</p><p>마감된 글도 수정할 수 있지만 다시 모집 중으로 전환되지는 않아요. 지난 모집 마감일을 그대로 유지하는 저장은 현재 서버가 허용하지 않아요.</p><p>글·팀장·기존 역할 ID와 지원·수락 관계는 유지됩니다. 기존 역할 삭제는 취소된 지원 이력을 확인할 수 없어 제한합니다.</p></div>,
    }} />}
  </section>
}
