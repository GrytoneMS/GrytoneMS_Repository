/** 공통 인증 연결을 완료하면 이 개발용 컴포넌트와 페이지의 호출부를 제거합니다. */
export function CreateIntegrationNotice() {
  return <aside className="post-dev" aria-label="개발용 인증 연동 안내">
    <strong>개발 중 · 인증 연동 대기</strong>
    <p>입력과 검증은 확인할 수 있어요. 현재는 실제 등록 요청을 보내지 않으며, 입력 내용은 이 화면에 유지됩니다.</p>
  </aside>
}
