import { ApiError } from '../../api/client'
import type { ApplicationSender } from './application-submit'
import { isPostMock } from './source'

// 실제 API 함수에는 예시 분기·사용자·토큰을 넣지 않습니다.
export function mockApplicationSender(outcome: string | null): ApplicationSender {
  return async () => {
    if (!isPostMock) throw new Error('개발용 모집글 예시 모드에서만 사용할 수 있습니다.')
    await new Promise(resolve => setTimeout(resolve, 800))
    const failures: Record<string, [number, string]> = {
      failure: [500, 'INTERNAL_ERROR'], duplicate: [409, 'ALREADY_APPLIED'], closed: [409, 'ROLE_CLOSED'],
      expired: [401, 'UNAUTHORIZED'], unverified: [403, 'UNVERIFIED'], blocked: [404, 'NOT_FOUND'], suspended: [403, 'SUSPENDED'],
    }
    const failure = failures[outcome ?? '']
    if (failure) throw new ApiError(failure[0], { success: false, code: failure[1], message: '개발용 지원 실패 예시입니다.' })
    return { id: 9001, status: 'PENDING' }
  }
}
