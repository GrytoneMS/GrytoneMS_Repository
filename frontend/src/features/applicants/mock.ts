import { ApiError } from '../../api/client'
import { mockDetail } from '../posts/mock'
import { isPostMock } from '../posts/source'
import { applyDecision, decisionBlock, type Application, type Snapshot } from './model'
import type { ManagementService } from './controller'

function fail(status: number, code: string): never { throw new ApiError(status, { success: false, code, message: '개발용 처리 오류 예시입니다.' }) }
export function createMockManagement(postId: string, query: URLSearchParams): ManagementService {
  if (!isPostMock) throw new Error('명시적인 개발용 모집글 예시 모드에서만 사용할 수 있어요.')
  let snapshot: Snapshot | null = null, processed = false
  const scenario = query.get('manage') ?? 'normal'
  const outcome = query.get('result') ?? 'success'
  const viewer = query.get('viewer') ?? 'owner'
  function initial(): Snapshot {
    const post = structuredClone(mockDetail(postId, viewer === 'owner' ? 'owner' : 'other'))
    const statuses = ['PENDING', 'PENDING', 'PENDING', 'ACCEPTED', 'REJECTED', 'CLOSED', 'LEFT'] as const
    const names = ['서연', '도현', '하은', '지우', '지원자 5', '지원자 6', '지원자 7']
    const applications: Application[] = statuses.map((status, index) => ({
      id: 501 + index, status, message: '함께 프로젝트를 완성하고 싶어요. 꾸준히 참여하겠습니다.', availableTime: index % 2 ? '화·목 오후' : '평일 19시 이후, 주말',
      createdAt: `2026-11-13T${String(9 + index).padStart(2, '0')}:00:00`,
      applicant: { id: 201 + index, nickname: names[index], schoolName: ['한국기술교육대학교', '순천향대학교', '선문대학교'][index % 3], major: index % 2 ? null : '컴퓨터공학', grade: index % 2 ? null : 2, profileImageUrl: null },
    }))
    const roles = post.roles.map((role, index) => ({ postRoleId: role.id, roleName: role.roleName, headcount: role.headcount, filledCount: role.filledCount,
      applications: scenario === 'empty' ? [] : index === 0 ? applications.filter(item => item.status !== 'ACCEPTED') : index === 1 ? applications.filter(item => item.status === 'ACCEPTED') : [],
    }))
    if (scenario === 'full') {
      post.roles[0].filledCount = post.roles[0].headcount
      post.roles[0].status = 'CLOSED'
      roles[0].filledCount = roles[0].headcount
    }
    if (scenario === 'closedPost') { post.status = 'CLOSED'; post.deadline = '2020-01-01' }
    // 현재 서버 조회는 CANCELED를 제외합니다. 취소 상태만 확인하는 명시적 계약 방어 예시입니다.
    if (scenario === 'canceled' && roles[0].applications[0]) roles[0].applications[0].status = 'CANCELED'
    return { post, roles }
  }
  return {
    async load(signal) {
      await new Promise<void>((resolve, reject) => {
        const abort = () => { clearTimeout(timer); reject(new DOMException('Aborted', 'AbortError')) }
        const timer = setTimeout(() => { signal.removeEventListener('abort', abort); resolve() }, scenario === 'slow' ? 2500 : 150)
        if (signal.aborted) abort(); else signal.addEventListener('abort', abort, { once: true })
      })
      if (viewer !== 'owner') fail(viewer === 'guest' ? 401 : 403, viewer === 'guest' ? 'UNAUTHORIZED' : 'FORBIDDEN')
      if (scenario === 'error' || processed && outcome === 'refreshFailure') fail(500, 'INTERNAL_ERROR')
      if (scenario === 'notFound') fail(404, 'NOT_FOUND')
      snapshot ??= initial()
      return structuredClone(snapshot)
    },
    async decide(id, decision, _reason) {
      await new Promise(resolve => setTimeout(resolve, 500))
      if (viewer !== 'owner') fail(403, 'FORBIDDEN')
      const errors: Record<string, [number, string]> = { failure: [500, 'INTERNAL_ERROR'], full: [409, 'ROLE_FULL'], processed: [409, 'INVALID_APPLICATION_STATUS'], expired: [401, 'UNAUTHORIZED'], forbidden: [403, 'FORBIDDEN'], notFound: [404, 'NOT_FOUND'], network: [0, 'NETWORK'] }
      if (errors[outcome]) { if (outcome === 'network') throw new TypeError('offline'); fail(...errors[outcome]) }
      snapshot ??= initial()
      if (decisionBlock(snapshot, id, decision)) fail(409, decision === 'accept' ? 'ROLE_FULL' : 'INVALID_APPLICATION_STATUS')
      const role = snapshot.roles.find(role => role.applications.some(item => item.id === id))!
      const roleClosed = decision === 'accept' && role.filledCount + 1 >= role.headcount
      const postClosed = roleClosed && snapshot.post.roles.every(item => item.id === role.postRoleId || item.status === 'CLOSED')
      const result = decision === 'accept' ? { applicationId: id, status: 'ACCEPTED' as const, roleClosed, postClosed } : { applicationId: id, status: 'REJECTED' as const }
      snapshot = applyDecision(snapshot, result); processed = true
      return result
    },
  }
}
