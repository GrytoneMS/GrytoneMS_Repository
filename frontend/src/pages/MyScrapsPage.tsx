import { useEffect, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { Button } from '../components/Button'
import { PostCard, PostError } from '../features/posts/components'
import { ScrapExamples } from '../features/scraps/ScrapContext'
import { useScraps } from '../features/scraps/context'
import type { createScrapController } from '../features/scraps/controller'

export default function MyScrapsPage() {
  const context = useScraps()
  return <section className="scrap-page" aria-labelledby="scrap-title">
    <ScrapExamples />
    <h1 id="scrap-title">스크랩</h1>
    <p className="post-note">최신 스크랩순 · 삭제·숨김된 글은 자동으로 사라져요.</p>
    {context && <ScrapList controller={context.controller} search={context.search} />}
  </section>
}
function ScrapList({ controller, search }: { controller: ReturnType<typeof createScrapController>; search: string }) {
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot, controller.getSnapshot)
  useEffect(() => {
    void controller.first()
    const refresh = () => { if (document.visibilityState === 'visible') void controller.refresh() }
    window.addEventListener('focus', refresh); document.addEventListener('visibilitychange', refresh)
    return () => { controller.cancelList(); window.removeEventListener('focus', refresh); document.removeEventListener('visibilitychange', refresh) }
  }, [controller])
  return <>
    <div className="scrap-tools"><Link to={`/posts${search}`}>모집글 둘러보기 →</Link><Button disabled={state.loading || Object.values(state.busy).some(Boolean)} onClick={() => void controller.first()}>목록 새로고침</Button></div>
    {state.error && <PostError message={state.error} retry={() => void controller.first()} />}
    <div className="post-grid scrap-grid" aria-busy={state.loading}>{state.items.map((row, index) => <PostCard key={row.scrapId} post={row.post} index={index} search={search} scrapList />)}</div>
    {state.loading && <p className="post-message" role="status">스크랩을 불러오는 중…</p>}
    {!state.loading && state.loaded && !state.error && !state.items.length && <div className="post-message"><h2>아직 스크랩한 모집글이 없어요</h2><p>관심 있는 모집글의 북마크를 눌러 모아 보세요.</p><Link className="button" to={`/posts${search}`}>모집글 둘러보기</Link></div>}
    {state.moreError && <PostError message={state.moreError} retry={() => void controller.more()} />}
    {state.hasNext && !state.error && !state.moreError && <div className="post-list-footer"><Button disabled={state.loading || Object.values(state.busy).some(Boolean)} onClick={() => void controller.more()}>스크랩 더 보기</Button></div>}
  </>
}
