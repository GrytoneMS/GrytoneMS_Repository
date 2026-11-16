import { ApiError } from '../../api/client'
import { mockPosts } from '../posts/mock'
import { isPostMock } from '../posts/source'
import { type Scrap, type ScrapService } from './api'

const examples = new Map<string, { rows: Scrap[]; sequence: number }>()
const fail = (status: number, code: string): never => { throw new ApiError(status, { success: false, code, message: '개발용 스크랩 예시' }) }
function wait(signal: AbortSignal, delay = 300) {
  return new Promise<void>((resolve, reject) => {
    const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
    const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, delay)
    if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true })
  })
}
export function mockScraps(params: URLSearchParams) {
  if (!isPostMock || params.get('scrapExample') !== 'true') throw new Error('명시적인 개발 예시에서만 사용할 수 있습니다.')
  const user = params.get('scrapUser') ?? 'alice', scenario = params.get('scrapCase') ?? 'normal', outcome = params.get('scrapResult') ?? 'success'
  const userId = user === 'owner' ? 1 : user === 'bob' ? 3 : user === 'guest' ? undefined : 2
  const key = `${user}:${scenario}:${params.get('scrapReset') ?? '0'}`
  if (!examples.has(key)) {
    const ids = scenario === 'empty' || user === 'guest' || user === 'owner' ? [] : scenario === 'last' ? [12] : user === 'bob' ? [3] : [12, 10, 8, 6, 4, 2]
    const rows = ids.map((postId, index) => {
      const p = mockPosts.find(p => p.id === postId)!
      return { scrapId: 100 - index, scrappedAt: p.createdAt, post: { ...p, roles: p.roles.map(r => ({ roleCode: r.roleCode, roleName: r.roleName, remaining: r.headcount - r.filledCount })), isScrapped: true } }
    })
    examples.set(key, { rows, sequence: 100 })
  }
  const example = examples.get(key)!
  let processed = false
  const service: ScrapService = {
    async load(cursor, signal) {
      await wait(signal, scenario === 'slow' ? 2500 : 300)
      if (!userId) fail(401, 'UNAUTHORIZED')
      if (scenario === 'error' || scenario === 'moreError' && cursor || outcome === 'refreshFailure' && processed) fail(500, 'INTERNAL_ERROR')
      // 삭제·숨김 글은 서버처럼 목록에서 제외. 실제 DTO에 없는 deleted/hidden 필드를 만들지 않습니다.
      const visible = scenario === 'restricted' ? example.rows.filter(r => ![10, 8].includes(r.post.id)) : example.rows
      const rows = visible.filter(row => !cursor || BigInt(row.scrapId) < BigInt(cursor))
      const items = rows.slice(0, 3).map(row => ({ ...row, post: { ...row.post, leader: { ...row.post.leader }, roles: row.post.roles.map(r => ({ ...r })) } }))
      return { items, hasNext: rows.length > 3, nextCursor: rows.length > 3 ? String(items.at(-1)!.scrapId) : null }
    },
    async change(postId, saved, signal) {
      await wait(signal, 650)
      if (!userId) fail(401, 'UNAUTHORIZED')
      if (outcome === 'failure') fail(500, 'INTERNAL_ERROR')
      if (outcome === 'expired') fail(401, 'UNAUTHORIZED')
      if (outcome === 'unverified' && saved) fail(403, 'UNVERIFIED')
      if (saved && (userId === 1 || outcome === 'own')) fail(400, 'CANNOT_SCRAP_OWN_POST')
      if (saved && (outcome === 'missing' || scenario === 'restricted' && [10, 8].includes(postId))) fail(404, 'NOT_FOUND')
      if (saved && !example.rows.some(row => row.post.id === postId)) {
        const post = mockPosts.find(p => p.id === postId)
        if (!post) return fail(404, 'NOT_FOUND')
        example.rows.unshift({ scrapId: ++example.sequence, scrappedAt: new Date().toISOString(), post: { ...post, roles: post.roles.map(r => ({ roleCode: r.roleCode, roleName: r.roleName, remaining: r.headcount - r.filledCount })), isScrapped: true } })
      } else if (!saved) example.rows = example.rows.filter(row => row.post.id !== postId)
      processed = true
      if (outcome === 'network') throw new TypeError('개발 예시: 처리 후 응답 유실')
      return { postId, isScrapped: saved }
    },
    async read(postId, signal) {
      await wait(signal)
      if (!userId) fail(401, 'UNAUTHORIZED')
      return { postId, isScrapped: example.rows.some(row => row.post.id === postId) }
    },
  }
  return { service, userId, values: Object.fromEntries(mockPosts.map(p => [p.id, example.rows.some(r => r.post.id === p.id)])) }
}
