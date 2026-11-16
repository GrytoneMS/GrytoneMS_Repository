import { ApiError } from '../../api/client'
import { mockPosts } from '../posts/mock'
import { isPostMock } from '../posts/source'
import { canDelete, type MyPost } from './api'
import type { MyService } from './controller'

function fail(code: string, status = 500): never { throw new ApiError(status, { success: false, code, message: '개발용 오류 예시입니다.' }) }
function wait(signal: AbortSignal, delay = 180) {
  return new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, delay)
    if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true })
  })
}
export function mockMyPosts(query: URLSearchParams): MyService {
  if (!isPostMock) throw new Error('명시적인 개발 모드에서만 사용할 수 있어요.')
  const scenario = query.get('mine') ?? 'normal', outcome = query.get('result') ?? 'success'
  let processed = false
  let posts: MyPost[] = mockPosts.map((post, index) => {
    const roles = post.roles.map(role => ({ id: role.id, roleCode: role.roleCode, roleName: role.roleName, headcount: role.headcount, filledCount: index % 2 ? 0 : role.filledCount, status: role.status,
      applicantCount: index % 2 ? 0 : role.filledCount + 2, pendingCount: index % 2 ? 0 : 1 }))
    return { id: post.id, title: post.title, category: post.category, status: post.status, deadline: post.deadline, dDay: post.dDay, createdAt: post.createdAt,
      closedAt: post.status === 'CLOSED' ? post.createdAt : null, roles, applicantCount: roles.reduce((n,r) => n+r.applicantCount,0), pendingCount: roles.reduce((n,r) => n+r.pendingCount,0) }
  })
  // 마감 탭에도 지원자 있음/없음 두 사례. 상세 화면과 상태를 공유하는 서버 저장소는 아닙니다.
  posts[10] = { ...posts[10], status: 'CLOSED', closedAt: posts[10].createdAt, roles: posts[10].roles.map(role => ({ ...role, status: 'CLOSED' })) }
  const copy = (post: MyPost): MyPost => ({ ...post, roles: post.roles.map(role => ({ ...role })) })
  return {
    async load(status, cursor, signal) {
      await wait(signal, scenario === 'slow' ? 2200 : 180)
      if (scenario === 'error' || scenario === 'moreError' && cursor || processed && outcome === 'refreshFailure') fail('INTERNAL_ERROR')
      const all = scenario === 'empty' ? [] : posts.filter(post => post.status === status && (!cursor || BigInt(post.id) < BigInt(cursor))).sort((a,b) => b.id-a.id)
      const items = all.slice(0,3).map(copy), hasNext = all.length > 3
      return { items, hasNext, nextCursor: hasNext ? String(items.at(-1)!.id) : null }
    },
    async close(id, signal) {
      await wait(signal, 300)
      if (outcome === 'failure') fail('INTERNAL_ERROR')
      if (outcome === 'network') throw new TypeError('offline')
      const post = posts.find(post => post.id === id)
      if (!post) fail('NOT_FOUND',404)
      if (post.status === 'CLOSED') fail('POST_ALREADY_CLOSED',409)
      posts = posts.map(post => post.id === id ? { ...post, status: 'CLOSED', closedAt: new Date().toISOString(), pendingCount: 0, roles: post.roles.map(role => ({ ...role, status: 'CLOSED', pendingCount: 0 })) } : post)
      processed = true
    },
    async remove(id, signal) {
      await wait(signal, 300)
      if (outcome === 'failure') fail('INTERNAL_ERROR')
      if (outcome === 'network') throw new TypeError('offline')
      const post = posts.find(post => post.id === id)
      if (!post) fail('NOT_FOUND',404)
      if (!canDelete(post) || outcome === 'hasApplicants') fail('POST_HAS_APPLICANTS',409)
      posts = posts.filter(post => post.id !== id); processed = true
    },
  }
}
