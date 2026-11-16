import { loadManagement, decideApplication } from './api'
import { isPostMock } from '../posts/source'
import type { PostRequestOptions } from '../posts/api'
import type { ManagementService } from './controller'
import type { AuthStore } from '../auth/session'

export function authenticatedManagement(postId: string, store: AuthStore): ManagementService {
  return {
    load: signal => store.execute((session, authSignal) => loadManagement(postId, { token: session.token, signal: AbortSignal.any([signal, authSignal]) })),
    decide: (id, decision, reason, signal) => store.execute((session, authSignal) => decideApplication(id, decision, reason, { token: session.token, signal: signal ? AbortSignal.any([signal, authSignal]) : authSignal })),
  }
}

export function managementService(postId: string, query: URLSearchParams, options: PostRequestOptions = {}): ManagementService {
  if (!isPostMock) return {
    load: signal => loadManagement(postId, { ...options, signal }),
    decide: (id, decision, reason, signal) => decideApplication(id, decision, reason, { ...options, signal }),
  }
  // 예시 사용자·처리 결과는 실제 요청 경로와 분리하며 개발 모드에서만 동적 로딩합니다.
  const mock = import('./mock').then(module => module.createMockManagement(postId, query))
  return { load: async signal => (await mock).load(signal), decide: async (id, decision, reason) => (await mock).decide(id, decision, reason) }
}
