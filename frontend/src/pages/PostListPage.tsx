import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { Input } from '../components/Input'
import { MockControls, PostCard, PostError } from '../features/posts/components'
import { isPostMock, loadPosts, readScenario } from '../features/posts/source'
import type { PostSummary, Sort } from '../features/posts/model'
import { postErrorMessage } from '../features/posts/errors'

export default function PostListPage() {
  const [params] = useSearchParams()
  return <PostListContent key={`${params.get('sort')}:${params.get('scenario')}`} />
}

function PostListContent() {
  const [params, setParams] = useSearchParams()
  const sort: Sort = params.get('sort')?.toUpperCase() === 'DEADLINE' ? 'DEADLINE' : 'LATEST'
  const scenario = readScenario(params.get('scenario'))
  const [items, setItems] = useState<PostSummary[]>([])
  const [cursor, setCursor] = useState<string | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [moreError, setMoreError] = useState('')
  const controller = useRef<AbortController | null>(null)
  const busy = useRef(false)
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next })
  useEffect(() => {
    const request = new AbortController()
    controller.current?.abort(); controller.current = request; busy.current = true
    loadPosts(sort, undefined, scenario, request.signal).then(page => {
      if (!request.signal.aborted) { setItems(page.items); setCursor(page.nextCursor); setHasNext(page.hasNext) }
    }).catch((failure: unknown) => {
      if (!request.signal.aborted) setError(postErrorMessage(failure))
    }).finally(() => { if (!request.signal.aborted) { setLoading(false); busy.current = false } })
    return () => { request.abort(); controller.current?.abort() }
  }, [sort, scenario, retry])
  async function loadMore() {
    if (!hasNext || !cursor || busy.current) return
    const request = new AbortController()
    controller.current = request; busy.current = true; setLoading(true); setMoreError('')
    try {
      const page = await loadPosts(sort, cursor, scenario, request.signal)
      if (!request.signal.aborted) {
        setItems(previous => [...previous, ...page.items.filter(item => !previous.some(existing => existing.id === item.id))])
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
      }
    } catch (failure) {
      if (!request.signal.aborted) setMoreError(postErrorMessage(failure))
    } finally { if (!request.signal.aborted) { setLoading(false); busy.current = false } }
  }
  const search = params.size ? `?${params.toString()}` : ''
  const retryFirst = () => { setLoading(true); setError(''); setRetry(value => value + 1) }
  return <section className="post-list" aria-labelledby="post-list-title">
    <MockControls scenario={scenario} onScenario={value => update('scenario', value)} />
    <div className="post-list-heading"><div><p className="post-eyebrow">천안·아산 대학생을 위한 팀원 모집</p><h1 id="post-list-title">어떤 팀원을 찾고 있나요?</h1></div>
      <div className="post-sort" role="group" aria-label="모집글 정렬"><Button aria-pressed={sort === 'LATEST'} onClick={() => update('sort', 'LATEST')}>최신순</Button><Button aria-pressed={sort === 'DEADLINE'} onClick={() => update('sort', 'DEADLINE')}>마감 임박순</Button></div>
    </div>
    <div className="post-search"><Input label="모집글 검색" placeholder="제목, 소개, 행사명 검색 · 2차 명세에서 제공 예정" disabled aria-describedby="post-search-note" /></div>
    <div className="post-filters" role="group" aria-label="준비 중인 모집글 필터">{['모집 유형', '역할', '팀장 학교', '모임 장소', '진행 방식', '활동 기간', '모집 중만 보기'].map(label => <Button key={label} disabled>{label}</Button>)}</div>
    <p id="post-search-note" className="post-note">검색·필터는 2차 API 명세에서 제공 예정입니다. {isPostMock && '정렬·더 보기는 개발용 데이터로 동작합니다.'}</p>
    {error ? <PostError message={error} retry={retryFirst} /> : <>
      <div className="post-grid" aria-busy={loading}>{items.map((post, index) => <PostCard key={post.id} post={post} index={index} search={search} />)}</div>
      <div className="post-list-footer" aria-live="polite">{loading ? <p role="status">{items.length ? '추가 모집글을 불러오는 중…' : '모집글을 불러오는 중…'}</p> : items.length === 0 ? <div className="post-message"><h2>아직 모집글이 없어요</h2><p>새로운 팀원 모집을 기다려 주세요.</p></div> : !hasNext && <p>모든 모집글을 확인했어요.</p>}</div>
      {moreError && <PostError message={moreError} retry={loadMore} />}
      {hasNext && cursor && !moreError && <div className="post-list-footer"><Button disabled={loading} onClick={loadMore}>모집글 더 보기</Button></div>}
    </>}
  </section>
}
