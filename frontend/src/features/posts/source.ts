import { getPost, getPosts, type PostRequestOptions } from './api'
import type { PostDetail, PostPage, Sort, Viewer } from './model'

export const isPostMock = import.meta.env.DEV && import.meta.env.VITE_POSTS_MOCK === 'true'
export type Scenario = 'normal' | 'empty' | 'error' | 'slow' | 'moreError'
export function readScenario(value: string | null): Scenario {
  return isPostMock && ['empty', 'error', 'slow', 'moreError'].includes(value ?? '') ? value as Scenario : 'normal'
}
export function readViewer(value: string | null): Viewer {
  return isPostMock && ['other', 'owner', 'applied', 'accepted', 'rejected', 'canceled', 'applicationClosed', 'left'].includes(value ?? '') ? value as Viewer : 'guest'
}
export async function loadPosts(sort: Sort, cursor: string | undefined, scenario: Scenario, signal: AbortSignal, options: PostRequestOptions = {}): Promise<PostPage> {
  if (!isPostMock) return getPosts({ sort, cursor, size: 20 }, { ...options, signal })
  await prepare(scenario, signal)
  const { mockList } = await import('./mock')
  if (scenario === 'moreError' && cursor) throw new Error('개발용 추가 로딩 오류입니다. 이 시나리오는 계속 실패합니다. 정상 상태로 전환해 주세요.')
  // 예시 페이지 크기는 더 보기 검증을 위해 8개. 응답 모양은 실제 API와 동일합니다.
  return scenario === 'empty' ? { items: [], nextCursor: null, hasNext: false } : mockList(sort, cursor, 8)
}
export async function loadPost(id: string, scenario: Scenario, signal: AbortSignal, viewer: Viewer = 'guest', options: PostRequestOptions = {}): Promise<PostDetail> {
  if (!isPostMock) return getPost(id, { ...options, signal })
  await prepare(scenario, signal)
  const { mockDetail } = await import('./mock')
  return mockDetail(id, viewer)
}
async function prepare(scenario: Scenario, signal: AbortSignal) {
  await new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, scenario === 'slow' ? 4000 : 350)
    if (signal.aborted) abort()
    else signal.addEventListener('abort', abort, { once: true })
  })
  if (scenario === 'error') throw new Error('개발용 오류 시나리오입니다. 정상 상태로 바꾼 뒤 재시도해 주세요.')
}
