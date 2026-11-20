// 직접 API 호출에서도 무토큰 요청을 차단합니다. 저장·세션 확인은 공통 인증 모듈이 담당합니다.
export const postCreateAuthPendingMessage = '로그인이 필요해요. 등록 요청은 보내지 않았고 입력 내용은 유지됩니다.'

export class PostCreateAuthPendingError extends Error {
  constructor() { super(postCreateAuthPendingMessage); this.name = 'PostCreateAuthPendingError' }
}
