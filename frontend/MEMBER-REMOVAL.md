# 팀장이 수락된 팀원 내보내기 (APPL-09)

## 기준·범위

- README 확인, `git fetch origin` 후 최신 `origin/dev=2320508`에서 `feature/front-APPL-09` 생성.
- 스크랩 PR #43은 OPEN으로 확인했고 가져오지 않았습니다. 기존 브랜치와 stash 3개를 보존했습니다.
- dev에는 본인 팀 나가기만 구현되어 있었으며 S08 팀장 내보내기 UI는 없었습니다.
- 지원자 관리의 수락된 팀원 패널에 기존 Button·배지·확인창을 재사용했습니다. 닉네임·역할·취소·확정, ESC 취소와 포커스 복귀는 기존 dialog 동작을 사용합니다.
- backend·DB·인증 저장 함수·프로필 등 다른 담당 화면은 변경하지 않았습니다. 사용자 예시 확인 완료 후 관련 파일만 커밋·일반 푸시하고 dev 대상 PR로 제출합니다. 원격 병합·강제 푸시는 범위 밖입니다.

## 확인한 계약

`ApplicationController`, `ApplicationService.leave`, `LeaveResponse`, `RoleSeatRepository`, `SecurityConfig`와 제공된 `API 명세서 최종.pdf`의 APPL-09 추가 명세 및 기능 명세서 8장 상태 규칙을 읽기 전용 대조했습니다.

- `PATCH /api/applications/{applicationId}/leave`, 본문 없음, Bearer 인증.
- 응답: `{ applicationId, status: "LEFT", roleReopened: boolean, postReopened: boolean }`.
- 같은 경로를 사용해도 지원자 본인 호출은 나가기, 해당 글 팀장 호출은 내보내기입니다. S08에서는 본인 글과 ACCEPTED 상태를 검사하며 최종 권한은 서버가 판단합니다.
- ACCEPTED만 LEFT 전환. 그 외 상태는 409 INVALID_APPLICATION_STATUS, 비팀장/비본인은 403, 없는 지원은 404입니다.
- 충원 수 감소와 역할·글 재모집은 서버가 처리합니다. 실제 코드는 마감일 당일까지 허용하고, 마감일 경과 시 기존 마감을 유지합니다. 삭제 글 재모집은 서버 쿼리에서 제외됩니다.
- 프론트는 LEFT 성공 상태만 확정하고 수락 인원·역할/글 상태를 계산하지 않습니다. 상세와 지원자 목록을 다시 조회해 표시합니다.
- Figma S08(`8:419`)을 읽기 전용 조회했으나 Starter MCP 호출 한도로 막혔습니다. 제공 PDF 폴더에는 S08 자료가 없어 기존 구현 디자인을 재사용했습니다. 원본 디자인 재대조는 미완료입니다.

## 요청·상태·인증

- 확인창 취소는 처리 요청을 호출하지 않습니다. 확정 후 controller의 동기 잠금으로 중복 요청을 차단합니다.
- 처리 실패는 기존 상태를 유지합니다. 401/403/404 권한·대상 오류는 기존 관리 정보를 숨깁니다.
- 처리 성공 후 재조회 실패는 완료 안내와 재조회 오류를 함께 표시합니다. LEFT를 ACCEPTED로 되돌리는 오래된 조회는 거부하며 재전송하지 않습니다.
- 응답 유실·잘못된 응답·5xx·상태 충돌은 성공으로 표시하지 않고 처리를 잠급니다. 목록·모집 현황 갱신으로만 상태를 확인할 수 있습니다.
- 기존 로그인 `readAccessToken`을 재사용합니다. 별도 저장·인증 우회 없음. 무토큰 보호 요청은 fetch 전에 차단합니다.
- storage/focus 및 저장소 동기화로 계정 변경 시 화면을 교체합니다. 응답 반영 직전에도 현재 토큰을 검사합니다. 모집글 변경·이탈 시 controller를 폐기하고 조회·처리 요청을 abort하여 늦은 응답을 무시합니다.
- 기존 수락·거절·역할 탭·프로필 링크와 본인 나가기 코드는 유지합니다. 이번 변경으로 직접 모집 마감 기능을 추가하지 않습니다.

## 개발 예시 확인

실행 중인 Node 프로세스를 확인했을 때 기존 Vite는 없었습니다. 환경 파일 변경 없이 프로세스에 `VITE_POSTS_MOCK=true`를 주고 `127.0.0.1:5174 --strictPort`로 서버 하나를 시작했습니다. 예시는 개발 빌드와 이 명시적 환경 설정에서만 사용합니다. 실제 API 실패를 예시 성공으로 대체하지 않습니다.

- 기본: http://127.0.0.1:5174/posts/1/applicants?viewer=owner
- 재모집: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&manage=reopen
- 마감 유지: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&manage=expiredClosed
- 응답 유실: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&result=responseLost
- 성공 후 재조회 실패: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&result=refreshFailure
- 이미 나간 팀원: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&result=alreadyLeft

1. 오른쪽 수락된 팀원의 내보내기를 누르면 닉네임·역할 확인창이 열리는지 확인합니다. 취소/ESC 후 팀원이 유지되어야 합니다.
2. 확정하면 처리 중 버튼이 잠기고 성공 후 팀원 목록에서 제거됩니다. 디자인 역할 탭에서 LEFT 상태와 감소한 인원을 확인합니다.
3. 재모집/마감 유지 예시에서 처리 후 글·역할 상태를 비교합니다.
4. 처리 결과 선택으로 실패·권한 없음·응답 유실·이미 나감을 확인합니다. 불명확한 결과는 재확정 대신 목록·모집 현황 갱신을 사용합니다.
5. 성공 후 재조회 실패는 완료 안내와 갱신 오류가 구분되는지 확인합니다. 기존 역할 탭·프로필·수락·거절도 확인합니다.

## 검증 구분

- 자동 검증: 최종 코드에서 build·lint, 신규 `check-member-removal.mjs` 13개, 기존 지원자 관리 17개, 내 지원 내역(본인 팀 나가기 포함) 22개, 마감·연락처 17개, 목록·상세·기존 경로 12개로 총 81개 통과. `git diff --check`와 새 파일 공백·충돌 표시 검사도 통과했습니다. 요청 가로채기·controller·정적 React 렌더 검증입니다.
- 실제 서버: JWT·백엔드·DB·CORS 통합 검증은 실행하지 않았습니다.
- 사용자 확인: 개발 예시에서 수락 → 내보내기 → 새 대기 지원자 수락이 정상 동작함을 사용자가 직접 확인했습니다. 기존 지원자의 지원 마감 유지와 안내 문구도 확인했습니다.
- 에이전트 브라우저: 브라우저 연결 호스트가 없고 최소화된 Chrome 접근을 시도한 뒤 Computer Use가 현재 URL을 확인하지 못해 입력을 중단했습니다. 사용자 확인과 별도로 에이전트의 실제 클릭·취소·모바일 시각 검증은 미완료입니다.

## 수락 → 내보내기 후 다른 지원자 수락 확인

기존 미커밋 작업은 수정 전 Temp의 `appl09-before-followup-20261116-195305.zip`에 별도 보관했습니다. 브랜치와 기존 stash는 그대로 유지합니다.

영상 편집 0/1의 기본 예시를 controller 연속 실행으로 재현했습니다.

| 단계 | 글 | 역할 | 인원 | A | 기존 B |
| --- | --- | --- | --- | --- | --- |
| 최초 | OPEN | OPEN | 0/1 | PENDING | PENDING |
| A 수락 | OPEN | CLOSED | 1/1 | ACCEPTED | CLOSED |
| A 내보내기·재조회 | OPEN | OPEN | 0/1 | LEFT | CLOSED |

실제 서버 accept는 정원 도달 시 같은 역할의 남은 대기를 CLOSED로 바꿉니다. leave는 역할·글만 재모집할 수 있고 CLOSED 지원을 PENDING으로 복원하지 않습니다. 예시도 이 정책과 일치합니다. 기존 B는 지원 상태 때문에 수락 버튼이 비활성이고 요청이 전송되지 않습니다. 내보내기 후 busy/loading/needsRefresh는 모두 false이며, 재조회한 역할 OPEN도 버튼 조건에 반영됩니다.

설명이 부족했던 화면을 수정했습니다. CLOSED 지원에는 빈자리가 생겨도 해당 지원을 다시 수락할 수 없다는 안내를 표시하고, 역할 CLOSED에는 빈자리와 별개로 마감 유지 사유를 표시합니다. 실제 API 모드도 같은 설명을 사용합니다. 프론트에서 지원이나 역할을 임의로 재개하지 않습니다.

- 원래 재현 확인: http://127.0.0.1:5174/posts/1/applicants?viewer=owner
- 새 PENDING 팀원으로 교체: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&manage=replacement
- 마감 유지: http://127.0.0.1:5174/posts/1/applicants?viewer=owner&manage=expiredClosed

교체 예시에서 영상 편집 A(서연)를 수락한 뒤 내보내면 재모집 후 새로 지원한 `새 지원자 B`가 나타납니다. 기존 도현·하은은 CLOSED를 유지합니다. 새 B는 PENDING이므로 수락할 수 있습니다. 신규 지원 도착은 예시 서버에서 모사하며 실제 API에서는 재조회된 서버 목록만 표시합니다.

신규 회귀 검증은 기존 B 마감 유지, A 수락→내보내기→새 B 수락, 실제 API 모듈+controller의 기존 PENDING B 연속 수락, 마감일 경과 시 PENDING 수락 차단을 확인합니다. 실제 API 모듈 검증도 fetch를 가로채므로 실서버 검증과 구분합니다. 이번 확인은 자동 검증이며 브라우저 클릭 검증을 추가 실행하지 않았습니다.
