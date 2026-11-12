import { useEffect, useState, useSyncExternalStore } from 'react'
import { useSearchParams } from 'react-router'
import { Button } from '../components/Button'
import { MockControls, PostCard, PostError } from '../features/posts/components'
import { isPostMock, loadPosts, readScenario, type Scenario } from '../features/posts/source'
import { ListFilters } from '../features/posts/ListFilters'
import { clearAllConditions, keywordError, listSearchParams, readListConditions, type PostListConditions } from '../features/posts/list-query'
import { createListLoader } from '../features/posts/list-loader'

export default function PostListPage() {
  const [params, setParams] = useSearchParams()
  const [searchReset, setSearchReset] = useState(0)
  const conditions = readListConditions(params)
  const scenario = readScenario(params.get('scenario'))
  const change = (update: (previous: PostListConditions) => PostListConditions) => setParams(previous => listSearchParams(update(readListConditions(previous)), previous))
  const canonical = listSearchParams(conditions, params).toString()
  return <section className="post-list" aria-labelledby="post-list-title">
    <MockControls scenario={scenario} onScenario={value => setParams(previous => { const next = new URLSearchParams(previous); next.set('scenario', value); return next })} />
    <div className="post-list-heading"><div><p className="post-eyebrow">천안·아산 대학생을 위한 팀원 모집</p><h1 id="post-list-title">어떤 팀원을 찾고 있나요?</h1></div>
      <div className="post-sort" role="group" aria-label="모집글 정렬"><Button aria-pressed={conditions.sort === 'LATEST'} onClick={() => change(previous => ({ ...previous, sort: 'LATEST' }))}>최신순</Button><Button aria-pressed={conditions.sort === 'DEADLINE'} onClick={() => change(previous => ({ ...previous, sort: 'DEADLINE' }))}>마감 임박순</Button></div>
    </div>
    <ListFilters key={searchReset} conditions={conditions} change={change} />
    {isPostMock && <p className="post-note">개발용 예시 모드입니다. 검색·필터의 실제 결과는 API 모드에서 확인해 주세요.</p>}
    {keywordError(conditions.keyword) ? <div className="post-message" role="alert"><p>{keywordError(conditions.keyword)}</p><Button onClick={() => change(previous => ({ ...previous, keyword: '' }))}>검색어 지우기</Button></div>
      : <PostListResults key={`${canonical}:${scenario}`} conditions={conditions} scenario={scenario} search={`?${canonical}`} clear={() => { setSearchReset(value => value + 1); change(clearAllConditions) }} />}
  </section>
}

function PostListResults({ conditions, scenario, search, clear }: { conditions: PostListConditions; scenario: Scenario; search: string; clear: () => void }) {
  // 조건별 새 loader로 목록·커서를 초기화하고, 이전 loader는 abort + 버전 검사로 폐기합니다.
  const [loader] = useState(() => createListLoader((cursor, signal) => loadPosts(conditions.sort, cursor, scenario, signal, {}, conditions)))
  const state = useSyncExternalStore(loader.subscribe, loader.getSnapshot, loader.getSnapshot)
  useEffect(() => { void loader.first(); return loader.cancel }, [loader])
  const { items, hasNext, nextCursor, loading, error, moreError } = state
  return error ? <PostError message={error} retry={() => void loader.first()} /> : <>
    <div className="post-grid" aria-busy={loading}>{items.map((post, index) => <PostCard key={post.id} post={post} index={index} search={search} />)}</div>
    <div className="post-list-footer" aria-live="polite">{loading ? <p role="status">{items.length ? '추가 모집글을 불러오는 중…' : '모집글을 불러오는 중…'}</p> : items.length === 0 ? <div className="post-message"><h2>조건에 맞는 모집글이 없어요</h2><p>검색어나 필터를 바꿔 다시 찾아보세요.</p><Button onClick={clear}>검색·필터 초기화</Button></div> : !hasNext && <p>모든 모집글을 확인했어요.</p>}</div>
    {moreError && <PostError message={moreError} retry={() => void loader.more()} />}
    {hasNext && nextCursor && !moreError && <div className="post-list-footer"><Button disabled={loading} onClick={() => void loader.more()}>모집글 더 보기</Button></div>}
  </>
}
