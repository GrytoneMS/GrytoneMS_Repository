// 인증 담당자의 공통 컨텍스트 연결 후 제거할 개발 단계 안내입니다. 인증 상태나 토큰을 저장하지 않습니다.
export const postCreateAuthPendingMessage = '인증 연동 전이라 등록 요청을 보내지 않았어요. 입력 내용은 그대로 유지됩니다.'

export class PostCreateAuthPendingError extends Error {
  constructor() { super(postCreateAuthPendingMessage); this.name = 'PostCreateAuthPendingError' }
}
