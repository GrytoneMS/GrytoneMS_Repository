# S19 관리자 웹 (ADMN-01~05)

## 기준과 변경 범위

- README와 Git 상태 확인 후 `git fetch origin`. 최신 dev `2320508`에서 `feature/front-ADMN-01`을 생성했습니다.
- 기존 관리자 프론트는 없었습니다. 미병합 PR #43(스크랩), #44(팀원 내보내기)은 가져오지 않았습니다. 기존 브랜치, stash 3개, Temp의 APPL-09 수정 전 백업 ZIP을 보존했습니다.
- 제공 폴더의 `S19 · 관리자 웹.pdf`, `Spec · S19 · 관리자 웹.pdf`를 텍스트 추출하고 PNG로 렌더해 읽기 전용 확인했습니다. 남색 왼쪽 메뉴, 표 중심 본문, 흰 패널·상태 탭·조치 버튼을 기존 Button 및 dev의 로고 에셋으로 구현했습니다. 예시 숫자·사람·학교를 실제 데이터로 하드코딩하지 않았습니다.
- 변경은 `/admin`, `/admin/:section` 라우트와 `features/admin` 및 관리자 검증·문서뿐입니다. 일반 학교 조회 함수·사용자 화면·backend·DB·환경 파일은 변경하지 않았습니다.
- `AdminReportController/ReportService`, `DocumentVerificationController/Service`, `AdminUserController/SuspensionService`, `SchoolController/Service`, DTO·Repository·SecurityConfig·CursorPage와 제공된 API 최종 명세를 대조했습니다. API 문서의 일부 '변경 예정' 표현보다 최신 구현 계약을 기준으로 삼았습니다.

## 실제 API 계약

| 기능 | 경로·메서드 | 요청·응답·정책 |
| --- | --- | --- |
| 권한 확인 | `GET /api/users/me` | 기존 로그인 토큰. `id,nickname,role` 검증 후 ADMIN만 진입 |
| 신고 목록 | `GET /api/admin/reports` | status=RECEIVED/RESOLVED, 생략 시 전체. 최신 ID 내림차순 cursor, size=20 |
| 신고 처리 | `PATCH /api/admin/reports/{id}/resolve` | `{action}`. SUSPEND만 `suspendDays:7/30/null`, `suspendReason:1~200자`. `{reportId,action,resolvedCount}` |
| 심사 목록 | `GET /api/admin/verifications` | status=PENDING/APPROVED/REJECTED. PENDING은 오래된 ID 오름차순, 처리완료는 내림차순. cursor, size=20 |
| 서류 확인 | `GET /api/admin/verifications/{id}/document` | Bearer 헤더로 바이너리 PNG/JPEG. no-store·redirect:error, Blob으로 표시 |
| 승인 | `PATCH /api/admin/verifications/{id}/approve` | 본문 없음. 심사 DTO 반환, APPROVED·같은 ID·documentUrl=null 확인 |
| 반려 | `PATCH /api/admin/verifications/{id}/reject` | `{reason}` 필수 1~200자. REJECTED·같은 ID·documentUrl=null 확인 |
| 직접 정지 | `POST /api/admin/users/{userId}/suspension` | `{days:7/30/null,reason,reportId}`. `{userId,status:SUSPENDED,endsAt}`. 관리자/이미 정지/탈퇴 사용자 처리는 서버가 거절 |
| 활성 학교 목록 | `GET /api/schools` | 일반 사용자와 같은 기존 목록. 전체 배열, 페이지네이션 없음 |
| 학교 등록 | `POST /api/admin/schools` | `{name,campus,emailDomain,region:CHEONAN/ASAN}`. 학교 DTO |
| 학교 수정 | `PATCH /api/admin/schools/{id}` | `{name,campus,emailDomain}`. 관리자 학교 DTO. 지역 수정은 지원하지 않음 |
| 비활성화 | `PATCH /api/admin/schools/{id}/deactivate` | 본문 없음, data=null. 기존 인증 회원은 유지 |

목록은 `{items,nextCursor,hasNext}`를 검증합니다. 최초 cursor 생략, 다음 cursor는 문자열 원문을 유지합니다. 안전 정수 밖 숫자 ID는 거부합니다. 중복 ID·역순/순환/행 불일치 커서·잘못된 응답을 성공 처리하지 않습니다.

신고 대상 내용은 관리자 신고 DTO의 target에서 읽습니다. 일반 공개 페이지로 우회하지 않습니다. NONE은 숨긴 모집글을 복구할 수 있습니다. HIDE/DELETE는 POST만, SUSPEND는 대상 작성자(POST 팀장/USER 본인/APPLICATION 지원자)를 대상으로 합니다. 같은 대상의 접수 신고는 서버에서 함께 처리합니다. 48시간 처리 기준은 안내로 표시하며 전체 신고 총수나 고유 신고자 수는 만들지 않았습니다.

## 구현한 인증·공통 동작

- 기존 `readAccessToken`을 재사용하며 별도 관리자 토큰 저장·계정 생성·권한 우회는 없습니다. 기존 로그인 화면으로 연결합니다. 권한 확인 중/비로그인/일반 사용자/확인 실패/ADMIN을 구분합니다.
- 실제 서비스는 권한 확인 전 보호 요청을 차단합니다. 프론트 표시와 별개로 서버 401·403을 처리하고 관리 목록·서류·입력을 제거합니다.
- 메뉴·상태·계정 변경 시 controller와 입력을 교체합니다. AbortSignal·세대로 이전 조회·서류·처리 응답을 폐기하고, 응답 반영 직전에도 현재 로그인 토큰을 검사합니다. storage/focus/주기적 동기화로 기존 인증 저장소 변경을 감지합니다.
- 확인창 취소는 요청을 보내지 않습니다. 확정은 동기 잠금으로 같은 tick 중복을 차단합니다. 실패 시 입력 원본을 유지하고 필드·중복·서버 오류를 표시합니다.
- 처리 성공 후 목록을 재조회합니다. 후속 조회 실패는 완료 안내를 유지하고 재처리를 잠급니다. 이미 심사·처리된 행의 오래된 PENDING/RECEIVED 응답은 다시 처리할 수 없게 거부합니다.
- 네트워크·응답 형식 오류·5xx는 반영 여부 불명확으로 표시하며 재전송 대신 조회를 제공합니다. 실제 실패를 예시 성공으로 바꾸지 않습니다.
- 서류 응답 URL은 정확한 관리자 상대 경로만 허용하고 해당 ID의 엔드포인트를 직접 호출합니다. 실제 이미지 타입·크기를 검사합니다. Blob·object URL은 메모리에서만 사용하고 닫기/조회/메뉴·계정 변경/처리 후에 제거·revoke합니다. 서류·URL·개인정보를 console이나 영구 저장소에 기록하지 않습니다. 화면 제거와 서버 파일 삭제 완료를 구분합니다.
- 관리자 CSS는 전용 클래스 아래 적용합니다. 좁은 화면에는 메뉴 줄바꿈과 키보드로 접근 가능한 표 가로 스크롤을 제공합니다. 확인창은 ESC 취소·포커스 복귀·배경 스크롤 잠금을 사용합니다.

## API 부족과 남은 통합 확인

- 관리자 사용자 검색·전체 사용자/정지 목록·정지 상태 조회 API가 없습니다. 이용 정지 메뉴는 실제 신고 응답에 있는 작성자 중 선택합니다. 더 보기로 대상을 추가 조회할 수 있습니다. 별도 사용자 검색 경로를 만들지 않았습니다.
- 직접 정지의 응답이 유실되면 신고 목록 재조회만으로 정지 여부를 확인할 수 없습니다. 이 경우 잠금을 유지하고 서버 측 확인이 필요하다고 안내합니다. 정지 해제 API는 존재하지만 이번 요청의 7일/30일/영구 정지 범위에 추가하지 않았습니다.
- 관리자 전체 학교 목록 API가 없습니다. 현재 활성 학교만 관리할 수 있으며 비활성 학교 전체 조회·선택은 누락 계약입니다. 비활성화를 성공시킨 뒤에는 활성 목록에서 제외됩니다.
- 인증 연결 대기 코드가 아니라 기존 로그인/권한 API까지 연결했습니다. 실제 ADMIN 계정으로 JWT·백엔드·DB·CORS·서류 다운로드를 통합 검증하는 작업은 남았습니다. 계정을 만들거나 role을 변경하지 않았습니다.

## 개발 예시와 사용자 체크리스트

기존 `5174` Vite 하나를 확인한 뒤 같은 서버를 재시작했습니다. 프로세스에만 `VITE_ADMIN_MOCK=true`와 기존 `VITE_POSTS_MOCK=true`를 설정했습니다. 환경 파일은 만들거나 수정하지 않았으며 `--strictPort`로 중복 실행을 막았습니다.

관리자 예시는 **개발 환경 + VITE_ADMIN_MOCK=true + adminExample=true**가 모두 필요합니다. production 또는 쿼리 없는 경로는 실제 모드입니다. 예시 데이터와 그림은 합성 자료이고 실제 인증 요청이나 실제 서류가 아닙니다. 예시 상태는 메모리에만 유지되며 조건 변경·새로고침·예시 초기화 시 초기화됩니다.

| 메뉴 | 주소 | 확인할 동작 |
| --- | --- | --- |
| 신고 처리 | http://127.0.0.1:5174/admin/reports?adminExample=true | 내용 펼치기, 접수/처리완료 탭, 더 보기. POST는 4조치, USER/APPLICATION은 문제없음·정지만. 확인창 취소/확정 후 목록 갱신 |
| 서류 인증 | http://127.0.0.1:5174/admin/verifications?adminExample=true | 서류 확인/닫기, 승인, 사유 필수 반려, 처리 후 이미지 제거·대기 목록 갱신, 승인/반려 탭 |
| 이용 정지 | http://127.0.0.1:5174/admin/suspensions?adminExample=true | 신고 대상 작성자 선택, 7일·30일·영구, 사유 입력, 취소/확정. 실제 정지 목록으로 오해하지 않기 |
| 학교 관리 | http://127.0.0.1:5174/admin/schools?adminExample=true | 학교 추가(지역 포함), 수정(지역 제외), 중복 도메인 오류, 비활성화 후 목록 제외 |

상단 사용자 선택으로 일반 사용자·비로그인을 확인합니다. 결과 선택으로 처리 실패·성공 후 조회 실패·응답 유실·401/403을 확인합니다. `&adminCase=empty`는 빈 목록입니다. 서류를 연 뒤 메뉴/사용자를 바꾸면 이전 그림이 사라져야 합니다. 쿼리 없는 `/admin/reports`는 실제 로그인 권한 확인을 수행합니다.

## 검증 결과

- 자동: `npm.cmd run build`, `npm.cmd run lint` 통과. `node scripts/check-admin.mjs` 17개 통과. 기존 9개 검증 스크립트 131개 통과. `git diff --check` 및 새 파일 공백·충돌 표시 검사 수행.
- 검증 방식: 요청 가로채기·controller 상태 전이·정적 React 렌더·합성 개발 예시입니다. 권한, 조회·커서, 신고 조치·정지 기간, 승인/반려, 서류 정리, 학교 변경, 실패·중복 요청·계정 전환을 다룹니다.
- 사용자 확인: 사용자가 관리자 S19 개발 예시 화면을 수동 확인했고 문제없음을 확인했습니다. 실제 백엔드 통합 검증과 구분합니다.
- 에이전트 브라우저: 연결 호스트가 없고 열려 있는 Edge가 최소화 상태여서 컴퓨터 사용 도구로 화면 접근에 실패했습니다. 에이전트의 실제 브라우저 메뉴 이동·클릭·확인창·모바일 시각 검증은 미완료입니다. 예시 주소 HTTP 200은 화면 검증으로 간주하지 않습니다.
- 실제 서버: 실행하지 않았습니다. 실제 관리자 조치나 권한 변경·DB 초기화 없음.
- 사용자 수동 확인 후 관리자 관련 파일과 필요한 라우트 변경만 커밋·일반 푸시하고 dev 대상 PR로 제출합니다. 원격 병합·강제 푸시는 수행하지 않습니다.
