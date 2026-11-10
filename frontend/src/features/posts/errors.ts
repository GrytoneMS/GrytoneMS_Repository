import { ApiError } from '../../api/client'

export function postErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'NOT_FOUND': return '모집글을 찾을 수 없어요. 삭제되었거나 숨김 처리된 글일 수 있습니다.'
      case 'UNAUTHORIZED': return '로그인이 필요하거나 만료됐습니다. 로그인 화면에서 다시 확인해 주세요.'
      case 'UNVERIFIED': return '학교 인증이 필요합니다.'
      case 'SUSPENDED': return '이용이 정지된 계정입니다.'
      case 'INTERNAL_ERROR': return '서버 오류로 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
      default: return error.message
    }
  }
  return error instanceof Error ? error.message : '모집글을 불러오지 못했습니다.'
}
