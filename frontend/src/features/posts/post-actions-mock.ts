import { ApiError } from '../../api/client'
import type { PostDetail } from './model'
import { closedPost, canReadContact, canClosePost } from './post-actions-controller'
import { isPostMock, loadPost, type Scenario } from './source'
import type { Viewer } from './model'

function failure(outcome: string | null) {
  const failures: Record<string, [number, string]> = { failure: [500, 'INTERNAL_ERROR'], forbidden: [403, 'FORBIDDEN'],
    expired: [401, 'UNAUTHORIZED'], missing: [404, 'NOT_FOUND'], alreadyClosed: [409, 'POST_ALREADY_CLOSED'] }
  const value = failures[outcome ?? '']
  if (value) throw new ApiError(value[0], { success: false, code: value[1], message: '개발용 실패 예시' })
}
async function prepare(signal: AbortSignal) {
  if (!isPostMock) throw new Error('명시적 개발 예시 모드에서만 사용할 수 있습니다.')
  await new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, 800)
    if (signal.aborted) abort()
    else signal.addEventListener('abort', abort, { once: true })
  })
}
/** 연락처는 일반 mock 상세에 추가하지 않고 별도 조회 성공 시에만 생성합니다. */
export function createMockPostActions(post: PostDetail, scenario: Scenario, viewer: Viewer, closing: string | null, contact: string | null) {
  let current = post
  return {
    async close(signal: AbortSignal) {
      await prepare(signal)
      if (!canClosePost(current)) throw new ApiError(current.isMine ? 409 : 403, { success: false, code: current.isMine ? 'POST_ALREADY_CLOSED' : 'FORBIDDEN', message: '' })
      if (closing === 'alreadyClosed') current = closedPost(current)
      failure(closing)
      current = closedPost(current)
    },
    async contact(signal: AbortSignal) {
      await prepare(signal)
      if (!canReadContact(current)) throw new ApiError(403, { success: false, code: 'FORBIDDEN', message: '' })
      failure(contact)
      return 'https://example.com/team-contact'
    },
    async load(signal: AbortSignal) {
      await prepare(signal)
      if (closing === 'refreshFailure' && current.status === 'CLOSED') throw new Error('개발용 재조회 실패')
      const loaded = await loadPost(String(post.id), scenario, signal, viewer)
      return current.status === 'CLOSED' ? closedPost(loaded) : loaded
    },
  }
}
