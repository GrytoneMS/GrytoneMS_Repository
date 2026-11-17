import { useLayoutEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { createScrapController } from './controller'
import { realScraps } from './api'
import { mockScraps } from './mock'
import { isPostMock } from '../posts/source'
import './scraps.css'
import { ScrapContext, useScraps, type ScrapContextValue as Context } from './context'
import type { AuthStore } from '../auth/session'
import { authenticatedScraps } from './source'

const keys = ['scrapExample', 'scrapUser', 'scrapCase', 'scrapResult', 'scrapReset']
export type ScrapSession = { userId: string; token: string }
export function ScrapProvider({ children, session, store, authScope }: { children: ReactNode; session?: ScrapSession; store?: AuthStore; authScope?: string }) {
  const { search } = useLocation()
  const query = new URLSearchParams(search)
  const mock = isPostMock && query.get('scrapExample') === 'true'
  const scope = mock ? new URLSearchParams(keys.filter(key => query.has(key)).map(key => [key, query.get(key)!])).toString() : JSON.stringify([authScope, session?.userId, session?.token])
  const context = useMemo<Context>(() => {
    const params = new URLSearchParams(mock ? scope : '')
    const example = mock ? mockScraps(params) : null
    const controller = createScrapController(example?.service ?? (store ? authenticatedScraps(store) : realScraps({ token: session?.token })), example?.values)
    return { controller, mock, ready: mock || !!session?.token, userId: mock ? example?.userId : session?.userId, token: mock ? undefined : session?.token, search: mock ? `?${params}` : '' }
  }, [scope, mock, session?.userId, session?.token, store])
  // 자식의 첫 목록 요청(passive effect)보다 먼저 활성화합니다. StrictMode 재마운트도 같은 순서입니다.
  useLayoutEffect(() => { context.controller.resume(); return context.controller.dispose }, [context])
  // 공통 인증 연결 시 userId와 인증 세션 변경값으로 scope를 구분합니다. 토큰을 저장하지 않습니다.
  return <ScrapContext.Provider value={context}>{children}<ScrapNotice controller={context.controller} /></ScrapContext.Provider>
}
function ScrapNotice({ controller }: { controller: Context['controller'] }) {
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot, controller.getSnapshot)
  return state.notice ? <p className="scrap-notice" role="status">{state.notice}</p> : null
}
export function ScrapButton({ postId, initial, own = false, title, compact = false }: { postId: number; initial: boolean; own?: boolean; title: string; compact?: boolean }) {
  const context = useScraps()
  if (!context) return <button type="button" disabled aria-label={`${title} 스크랩`}>스크랩</button>
  return <LiveScrapButton context={context} postId={postId} initial={initial} own={own} title={title} compact={compact} />
}
function LiveScrapButton({ context, postId, initial, own, title, compact }: { context: Context; postId: number; initial: boolean; own: boolean; title: string; compact: boolean }) {
  const state = useSyncExternalStore(context.controller.subscribe, context.controller.getSnapshot, context.controller.getSnapshot)
  const saved = state.values[postId] ?? initial, busy = !!state.busy[postId], error = state.errors[postId], needsCheck = state.needsCheck[postId]
  const unknown = typeof saved !== 'boolean'
  const blocked = own && !saved
  return <div className={`scrap-control${compact ? ' scrap-compact' : ''}`}>
    <button type="button" className="scrap-button" aria-pressed={saved === true} aria-label={`${title} 스크랩 ${saved ? '해제' : '등록'}`} disabled={context.ready === false || busy || blocked || unknown || needsCheck}
      title={blocked ? '내가 올린 모집글은 스크랩할 수 없어요.' : unknown ? '스크랩 상태를 조회한 뒤 시도해 주세요.' : undefined}
      onClick={event => { event.preventDefault(); event.stopPropagation(); void context.controller.change(postId, saved, own) }}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill={saved ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M6 3h12v18l-6-4-6 4z" /></svg>{!compact && (busy ? '처리 중…' : saved ? '스크랩 해제' : '스크랩')}
    </button>
    {error && <p className="scrap-error" role="alert">{error}</p>}
    {needsCheck && <button type="button" className="scrap-recheck" disabled={busy} onClick={event => { event.preventDefault(); event.stopPropagation(); void context.controller.recheck(postId) }}>스크랩 상태 다시 조회</button>}
    {!compact && blocked && <p className="post-note">내가 올린 모집글은 스크랩할 수 없어요.</p>}
  </div>
}
export function ScrapExamples() {
  const context = useScraps(), [params, setParams] = useSearchParams()
  if (!context?.mock) return null
  const update = (key: string, value: string) => setParams(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next })
  return <aside className="post-dev"><strong>개발용 스크랩 예시 · 실제 계정/DB 저장 없음</strong><div>
    <label>사용자<select value={params.get('scrapUser') ?? 'alice'} onChange={e => update('scrapUser', e.target.value)}>{['alice', 'bob', 'guest', 'owner'].map(v => <option key={v}>{v}</option>)}</select></label>
    <label>목록<select value={params.get('scrapCase') ?? 'normal'} onChange={e => update('scrapCase', e.target.value)}>{Object.entries({ normal: '정상', empty: '빈 목록', last: '마지막 1개', error: '조회 실패', moreError: '더 보기 실패', slow: '느린 조회', restricted: '삭제·숨김 자동 제외' }).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></label>
    <label>처리<select value={params.get('scrapResult') ?? 'success'} onChange={e => update('scrapResult', e.target.value)}>{Object.entries({ success: '성공', failure: '실패', expired: '인증 만료', unverified: '학교 미인증', own: '서버 본인 글 거절', missing: '삭제·숨김 등록 거절', network: '처리 후 응답 유실', refreshFailure: '성공 후 목록 조회 실패' }).map(([v, label]) => <option key={v} value={v}>{label}</option>)}</select></label>
    <button type="button" onClick={() => update('scrapReset', String(Date.now()))}>예시 초기화</button>
    <Link to={`/me/scraps${context.search}`}>내 스크랩</Link><Link to={`/posts${context.search}`}>모집글 목록</Link>
  </div></aside>
}
