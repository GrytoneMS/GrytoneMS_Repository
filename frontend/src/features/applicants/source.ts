import { loadManagement, decideApplication } from './api'
import { isPostMock } from '../posts/source'
import type { PostRequestOptions } from '../posts/api'
import type { ManagementService } from './controller'

export function managementService(postId: string, query: URLSearchParams, options: PostRequestOptions = {}): ManagementService {
  if (!isPostMock) return {
    load: signal => loadManagement(postId, { ...options, signal }),
    decide: (id, decision, reason) => decideApplication(id, decision, reason, options),
  }
  // 예시 사용자·처리 결과는 실제 요청 경로와 분리하며 개발 모드에서만 동적 로딩합니다.
  const mock = import('./mock').then(module => module.createMockManagement(postId, query))
  return { load: async signal => (await mock).load(signal), decide: async (id, decision, reason) => (await mock).decide(id, decision, reason) }
}
