# 모집글 수정 POST-02

## 작업 기준과 변경 범위

- 시작 시 `feature/front-APPL-11` 작업 트리는 깨끗했습니다. 기존 브랜치/커밋을 그대로 보존했습니다.
- `git fetch origin` 후 최신 `origin/dev` (`2b9d2e2`, PR #36 병합 커밋)에서 `feature/front-POST-02`를 생성했습니다.
- 시작·완료 시점 GitHub 조회 모두 PR #38은 **OPEN, mergedAt=null**입니다. #38 변경을 병합하거나 cherry-pick하지 않았습니다.
- 내 모집글은 dev의 placeholder를 그대로 두었습니다. backend, 인증/프로필, 내 지원 내역 파일은 변경하지 않았습니다. 최초 구현 단계에서는 커밋·푸시·PR 생성·병합 없이 작업했고, 이후 사용자 요청으로 검토 후 커밋·일반 푸시·dev 대상 PR 생성을 진행합니다. PR 병합은 범위 밖입니다.

변경 파일:

| 파일 | 내용 |
| --- | --- |
| `src/App.tsx` | `/posts/:postId/edit` 라우트 |
| `src/layouts/AppLayout.tsx` | 수정 경로에도 기존 모집 전용 레이아웃 적용 |
| `src/pages/PostEditPage.tsx` | 초기 조회·오류/재시도·수정 상태·저장/취소·개발 예시 |
| `src/pages/PostCreatePage.tsx` | 기존 폼/스타일을 수정에도 쓰는 선택적 editor 입력. 작성 경로·검증·요청은 유지 |
| `src/pages/PostDetailPage.tsx` | 본인 글 수정 링크, 수정 완료와 후속 조회 실패 안내 |
| `src/features/posts/edit-model.ts` | 기존 데이터 조회/완전성 검사·수정 전용 검증·ID 보존 요청·실제 PUT·오류 변환 |
| `src/features/posts/edit-submit.ts` | 동시 제출 잠금·취소 신호·늦은 응답 무시 |
| `src/features/posts/edit-examples.ts` | 명시적 개발 예시와 메모리 저장 |
| `scripts/check-post-edit.mjs` | 요청 가로채기·상태·정적 렌더 검증 |
| `scripts/check-posts.mjs` | 수정 경로 레이아웃 및 관계없는 경로 제외 회귀 검증 |
| `POST-EDIT.md` | API 근거·제약·확인 방법·후속 연결 위치 |

## 확인한 계약과 근거

외부 제공 문서 `C:/Users/user/Documents/카카오톡 받은 파일/API 명세서 최종.pdf`의 공통 규칙, 상세, 작성, 연락처 및 `PUT /api/posts/{postId} (POST-02)` 항목을 확인했습니다. 이전 `그려톤_API 명세서 (1차).pdf`에는 수정 endpoint가 없어 최신 문서/서버를 기준으로 합니다.

실제 근거 파일:

- `backend/src/main/java/com/grytonems/backend/domain/post/PostController.java`: `updatePost`.
- `backend/src/main/java/com/grytonems/backend/domain/post/dto/PostCreateRequest.java`: 수정도 작성 DTO 사용, `RoleRequest.id`.
- `backend/src/main/java/com/grytonems/backend/domain/post/PostService.java`: `updatePost`, `updateRoles`, `updateEvent`, `validate`, `getPost`.
- `backend/src/main/java/com/grytonems/backend/domain/post/dto/PostDetailResponse.java`: 상세 응답 필드.
- `backend/src/test/java/com/grytonems/backend/domain/post/PostUpdateApiTest.java`: 수정·역할 ID/추가/삭제·지원 제한·역할 재모집·연락처 알림·행사·권한 테스트를 읽었습니다. 백엔드 테스트 실행은 하지 않았습니다.
- `backend/src/main/java/com/grytonems/backend/domain/application/ApplicationController.java`, `ApplicationService.java`: `getApplicants`, `getContact`.
- `backend/src/main/java/com/grytonems/backend/global/security/SecurityConfig.java`, `AuthUser.java`, `JwtAuthenticationFilter.java`: 인증 정책.

| 항목 | 실제 계약 및 구현 |
| --- | --- |
| 저장 | `PUT /api/posts/{postId}`, 작성 DTO 전체 본문, `200 { success: true, data: PostDetailResponse }`. PATCH가 아님 |
| 수정 가능 | 제목·유형·소개·활동 기간·모집 마감일·진행 방식·장소·빈도·역할·행사·연락처 |
| 변경 불가 | 글 ID·팀장·작성일·글/역할 상태·충원 수·지원/수락 관계는 수정 본문에 넣지 않음. 기존 역할 ID는 식별용으로 유지 |
| 글 상태 | OPEN/CLOSED 모두 수정 가능. CLOSED 글 자체는 다시 열리지 않음. CLOSED 글에 추가한 역할도 CLOSED. OPEN 글의 마감 역할을 증원하면 서버가 빈자리 확인 후 역할을 재개할 수 있음 |
| 역할 ID | 기존 역할은 해당 ID, 새 역할은 id=null. 서버는 생략한 기존 역할을 삭제로 해석하며 남의 ID·중복 ID를 400으로 거절 |
| 역할 제한 | 취소 제외 지원 기록(PENDING/ACCEPTED뿐 아니라 REJECTED/CLOSED/LEFT 포함)이 있으면 분류 변경/정원 감소 불가. 이름·조건 변경 및 5명 이내 증원 가능 |
| 삭제 제한 | 취소 기록까지 포함해 지원 기록이 하나라도 있으면 기존 역할 삭제 불가 |
| 날짜 | 모집 마감일 ≥ KST 오늘, 마감일 ≤ 시작일 ≤ 종료일. 기존 날짜라고 예외를 주는 코드가 없음. 행사 접수 마감일은 올바른 날짜/필수 여부만 확인 |
| 인증 | Bearer JWT와 본인 글 조건. 수정 PUT에는 작성 POST의 VERIFIED 전용 matcher가 적용되지 않고 로그인 필요 조건이 적용됨. 임의 학교 인증 조건을 추가하지 않음 |
| 초기값 | 로그인 상태의 상세 GET → isMine 확인 → GET contact와 GET applications. 모두 성공하고 역할/인원 현황이 맞아야 폼을 표시 |
| 연락처 | 상세에 없음. `/api/posts/{postId}/contact`는 팀장도 조회 가능. 빈 문자열 대체 금지, URL 정규화도 하지 않고 원문 보존. 링크 변경은 서버의 CONTACT_CHANGED 알림 대상 |
| 검증 차이 | 작성은 기존 검증 유지. 수정은 별도 `validateEditDraft`. 서버는 링크 필수/길이만 검사하므로 기존 비표준 URL도 유지 가능. 수정에서 작성 화면의 시작일 min=오늘 제약을 강제하지 않음 |

## 발견한 API 제약과 현재 처리

1. **역할 삭제 가능 여부를 완전하게 조회할 API가 없습니다.** `ApplicationService.getApplicants`는 `CANCELED`를 제외하지만 `PostService.updateRoles`의 삭제 검사는 취소 포함 전체 지원 기록을 봅니다. 목록이 비었다고 삭제 가능하다고 판단할 수 없습니다. 기존 역할 삭제를 보수적으로 제한하고 이유를 화면에 표시합니다. 새로 추가한 미저장 역할은 삭제할 수 있습니다. 서버에서 역할별 `hasAnyApplication`/`canDelete`를 제공하면 해당 제한을 정확하게 풀 수 있습니다. 비취소 지원 유무로 확인 가능한 분류 변경·감원 제한은 그대로 적용합니다.
2. **마감 글도 수정 가능하지만 지난 모집 마감일 유지 저장은 불가능합니다.** `updatePost`가 작성과 같은 `validate(request)`를 호출하여 과거 마감일을 거절합니다. 날짜를 자동 변경하거나 무시하지 않고 기존 값을 표시한 뒤 검증 오류/이유를 안내합니다. 지난 활동의 날짜를 유지하면서 본문만 고치는 흐름은 백엔드 계약 보완이 필요합니다.
3. **공통 인증 컨텍스트·토큰 공급 경로가 없습니다.** 기존 API client의 명시적 token 옵션을 유지했습니다. 별도 로그인/토큰 저장/가짜 헤더는 추가하지 않았습니다. 현재 실제 화면은 인증 연동 대기로 초기 조회도 안전하게 차단합니다. API/submitter 수준에서도 토큰 없는 실제 저장을 차단하고 입력을 유지합니다. 추후 공통 인증을 `loadEditSnapshot`, `updatePost`, 상세 조회의 옵션에 전달하고 사용자/인증 변경 시 편집 상태를 폐기해야 합니다.
4. 링크 URL 형식에 관한 작성의 추가 프론트 검증을 수정에는 그대로 강제하지 않습니다. 최신 DTO/Service의 필수·길이 계약에 맞추어 기존 문자열을 유지합니다.

## 저장·조회·이탈 동작

- 폼은 상세·연락처·지원 현황 전체 로딩 후 한 번 초기화합니다. 편집 시작 후 자동 재조회하지 않고 초기 props 변경으로 draft를 다시 덮어쓰지 않습니다. 초기 요청마다 AbortController를 두고 요청 폐기 후 늦게 온 결과를 무시합니다.
- 검증 오류/서버 필드 오류를 필드와 요약에 표시하고 첫 오류로 포커스를 이동합니다. 실패 시 입력은 초기화하지 않습니다.
- 같은 tick 연속 제출도 submitter에서 1회만 처리합니다. 제출 중 입력·저장·취소 버튼이 잠깁니다. 페이지를 떠나면 응답 반영을 중단합니다. 요청 abort가 서버 트랜잭션 취소까지 보장하지는 않습니다.
- 409 ROLE_HAS_APPLICANTS는 수정 중 지원 현황 변경 가능성을 안내합니다. 인증·권한·삭제·일반 필드·서버 실패도 구분합니다.
- 성공 후 `/posts/{postId}`로 이동하고 상세를 새로 조회합니다. history state에 저장 성공을 전달하므로 GET 실패는 ‘저장은 완료됐지만 최신 상세 조회 실패’로 표시하고 조회만 재시도합니다. PUT을 재실행하지 않습니다.
- 네트워크/잘못된 envelope는 저장 여부가 불확실하므로 상세 확인 후 재시도를 안내합니다. 실제 실패를 mock 성공으로 바꾸지 않습니다.
- 취소는 PUT 없이 상세로 복귀합니다. draft는 현재 화면에서만 보존하며 임시저장/새로고침 복원은 없습니다.
- 프로젝트에 기존 SPA 이탈 확인 방식은 없습니다. 변경 시 브라우저 새로고침/닫기에는 beforeunload를 사용합니다. 앱 내부 메뉴·뒤로가기·취소에는 별도 확인창을 추가하지 않았습니다.

## 직접 확인 주소와 체크리스트

기존 **http://127.0.0.1:5173** 개발 서버가 `VITE_POSTS_MOCK=true`로 응답함을 확인했습니다. 해당 서버를 종료하지 않았고 추가 서버는 실행하지 않았습니다. `--strictPort` 확인 실행은 기존 포트 충돌로 종료되었습니다.

- 정상 수정: http://127.0.0.1:5173/posts/1/edit?editExample=success
- 본인 상세 진입: http://127.0.0.1:5173/posts/1?viewer=owner
- 실제 API 인증 대기: http://127.0.0.1:5173/posts/1/edit
- 기존 작성: http://127.0.0.1:5173/posts/new

예시 전제: 개발 서버 + `VITE_POSTS_MOCK=true` + 명시적 `editExample` 쿼리. production에서는 쿼리로 예시를 켤 수 없습니다. 실제 모드는 실패해도 예시로 전환하지 않습니다. 예시 저장은 현재 탭의 모듈 메모리에만 남으며 새로고침으로 초기화됩니다. 실제 API·DB 저장은 없습니다.

상단 선택창에서 바꾸거나 위 수정 주소의 쿼리를 다음 값으로 변경합니다.

| editExample | 직접 확인 |
| --- | --- |
| success | 모든 기존 값 확인 → 제목/역할 이름/연락처 수정 → 연속 클릭 → 상세 이동 및 수정 내용 확인 |
| invalid | 입력을 바꾼 후 저장 → 제목·역할 이름 서버 필드 오류와 입력 유지 |
| forbidden | 본인 글이 아닌 초기 조회 403 |
| restricted | 편집 후 저장 → 뒤늦은 지원 현황 변경 409, 입력 유지 |
| failure | 편집 후 저장 → 서버 500, 입력 유지·재시도 가능 |
| loadFailure / contactFailure | 초기 조회/연락처 조회 실패, 폼과 저장 버튼 없음, 재시도 가능. 실패 예시는 재시도해도 실패하므로 선택창에서 정상으로 전환 |
| missing / deleted | 없는 글/삭제된 글 안내 |
| closed | 마감 글의 기존 과거 마감일 확인 → 그대로 저장 시 날짜 오류 → 적절한 날짜로 변경 시 저장 가능, 글은 CLOSED 유지 |
| refreshFailure | 저장 성공 후 상세 GET 실패 안내. 수정 재전송 없이 조회만 재시도. 상세 예시 시나리오를 정상으로 바꾸어 복구 확인 |
| noAuth | 예시 초기값을 편집한 뒤 저장 → 실제 저장 경로의 무토큰 차단, 입력 유지·fetch 없음 |

추가 확인:

- 두 번째 역할은 지원 이력이 있어 분류 잠금·최소 인원 2명. 이름/조건과 증원은 가능.
- 첫 번째 역할도 취소 이력 조회 부재로 기존 삭제는 잠금. 새 역할 추가 후 그 역할 삭제는 가능.
- 취소 시 상세 복귀, 저장 내용 변경 없음.
- 작성·지원 모달·지원자 관리·수동 마감·연락처 기능의 기존 동작 유지.
- PC/모바일 폭에서 폼 줄바꿈, 오류/버튼 가독성 확인. 기존 작성의 1000/760/440px 반응형 스타일을 그대로 사용합니다.

## 검증 결과

- `npm.cmd run build`: 통과.
- `npm.cmd run lint`: 경고/오류 없이 통과.
- `node scripts/check-post-edit.mjs`: 11개 통과.
- 기존 `check-post-create.mjs` 14개, `check-posts.mjs` 12개, `check-post-actions.mjs` 17개, `check-applicants.mjs` 17개, `check-applications.mjs` 10개, `check-post-filters.mjs` 14개: 통과. 합계 95개.
- `git diff --check`: 통과. backend·내 모집글 관련 diff 없음.
- 검증은 fetch 가로채기, 제출 상태, 정적 React 렌더입니다. 실제 JWT/백엔드·DB 저장·CORS 검증은 하지 않았습니다.
- 에이전트의 실제 PC/모바일 브라우저 비교는 연결 호스트 부재 및 창 접근 시간 초과로 완료하지 못했습니다. 이후 **사용자가 레이아웃 수정 후 수정 화면 정상 표시를 직접 확인했습니다.** 사용자 확인 환경의 화면 검증이며, PC·모바일 각각의 확인이나 실제 JWT/백엔드 저장 성공 검증으로 확대 해석하지 않습니다.

## PR #38 병합 후 남은 연결

#38을 가져오지 않고 원격 파일 내용만 읽어 연결 위치를 확인했습니다.

- `frontend/src/pages/MyPostsPage.tsx` 카드 액션의 `<Button disabled title="모집글 수정 화면 미구현">수정 · 준비 중</Button>`를 `/posts/${post.id}/edit` Link로 바꿉니다. 기존 지원자 관리 링크 옆입니다.
- 예시 모드에서는 `/posts/${post.id}/edit?editExample=success`를 사용하고 실제 모드에는 예시 쿼리를 붙이지 않습니다.
- ‘수정 기능은 준비 중입니다’ 문구를 제거/갱신합니다.
- CLOSED도 수정 가능한 서버 계약이므로 상태만으로 수정 링크를 비활성화하지 않습니다. 숨김/삭제/본인 여부/인증은 서버 및 초기 조회 결과로 확인합니다.
- #38의 내 모집글 회귀 검증을 추가로 실행합니다. 이번 브랜치에는 해당 파일/스크립트를 가져오지 않았습니다.
- 공통 인증 연결과 역할 삭제 가능 정보/과거 날짜 보존 정책은 #38 병합과 별개의 후속 과제입니다.

## 수정 화면 레이아웃 누락 보완 (2026-11-16)

- `src/layouts/AppLayout.tsx`의 기존 경로 정규식에 `edit`만 추가했습니다. 수정도 `.posts-layout` 안에 렌더되며 색상 변수/CSS 중복 선언은 없습니다.
- `scripts/check-posts.mjs`에 수정 주소(예시 쿼리 포함)의 모집 레이아웃·헤더·본문 적용과 관계없는 하위 주소 제외 회귀 검증을 추가했습니다. 기존 목록·상세·작성·지원자 관리 레이아웃 검증도 통과했습니다.
- build·lint·관련 자동 검증 95개가 통과했습니다(`check-posts` 12, 작성 14, 수정 11, 지원자 관리 17, 마감/연락처 17, 지원 10, 필터 14).
- 에이전트의 동일 너비 시각 비교는 브라우저 연결 호스트 부재로 미완료입니다. 이후 사용자가 수정 화면 정상 표시를 직접 확인했습니다. 코드상 작성·수정은 동일 폼/스타일과 모집 레이아웃을 사용합니다.
- 직접 비교: `http://127.0.0.1:5173/posts/new`와 `http://127.0.0.1:5173/posts/1/edit?editExample=success`를 같은 창 너비에서 열어 배경·입력창·유형 선택 상태·파란 제출 버튼을 확인합니다. 작성의 기본 유형과 수정 예시의 유형은 모두 공모전입니다.
- 이전 미커밋 작업은 보존했으며 커밋·푸시·PR 생성 및 GitHub 조회는 하지 않았습니다.

## PR 준비 시 재확인 (2026-11-16)

- 사용자 요청에 따라 `git fetch origin` 및 PR #38 상태를 재확인했습니다. #38은 OPEN, mergedAt=null이며 `origin/dev`는 `2b9d2e2`로 작업 기준과 동일합니다. 추가 dev 변경을 합치거나 #38 변경을 가져올 필요가 없습니다.
- 내 모집글의 ‘수정 · 준비 중’ 연결은 위 후속 연결 목록에 남깁니다. 상세의 본인 글 수정 링크는 구현되어 있습니다.
- 사용자 수정 화면 시각 확인 완료와 실제 인증 저장 미검증을 PR에 구분해 기록합니다. 지난 마감일 유지 저장 제한·기존 역할 삭제 제한·공통 인증 공급 경로 부재도 남은 제약입니다.
- 이번 작업 관련 파일만 명시적으로 스테이징하고 환경 파일·비밀값·로그·임시 파일은 제외합니다. 일반 푸시와 dev 대상 PR 생성만 진행하며 강제 푸시·PR 병합은 하지 않습니다.
