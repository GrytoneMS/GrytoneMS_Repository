# 내 지원 내역 S12 (APPL-10)

## 작업 기준

- README와 깨끗한 Git 작업 트리를 확인한 뒤 `git fetch origin`을 실행했습니다.
- PR #37(인증/프로필), #38(내 모집글), #39(모집글 수정)는 모두 OPEN, mergedAt=null입니다. 임의 병합/cherry-pick하지 않았습니다.
- 최신 `origin/dev` `2b9d2e2`에서 README 규칙대로 `feature/front-APPL-10`을 생성했습니다. ID는 최신 API 문서와 `ApplicationController.getMyApplications`의 APPL-10에서 확인했습니다.
- 기존 POST-02 커밋/브랜치를 보존했습니다. 이번 브랜치에는 미병합 POST-02/인증/내 모집글 변경이 없습니다.
- 최초 구현 단계에서는 커밋·푸시·PR 생성 없이 작업했습니다. 이후 사용자 요청으로 검토 후 커밋·일반 푸시·dev 대상 PR 생성을 진행합니다. 원격 PR 병합은 하지 않습니다. backend·인증·프로필 화면·내 모집글은 수정하지 않았습니다.

## 확인 자료와 디자인 차이

- Figma S12: https://www.figma.com/design/TQAHXMAyHdCqU5aV3qBmhW/?node-id=17-481
- 읽기 전용 `get_design_context`로 상세 레이아웃/스타일과 스크린샷을 확인했습니다. Figma 쓰기 작업은 하지 않았습니다.
- 제공 폴더 `C:/Users/user/Downloads/멋사 그려톤_팀 모임 웹사이트 (1)`의 `S12 · 내 지원 내역.pdf`, `Spec · S12 · 내 지원 내역.pdf`를 추출/렌더하여 확인했습니다.
- Spec은 APPL-10 상태별 내역, APPL-07 수락자 연락처, APPL-03 대기만 취소/재지원 불가, APPL-09 팀 나가기 확인 팝업, SAFE-04 안전 수칙을 명시합니다. 따라서 팀 나가기도 포함했습니다.
- `C:/Users/user/Documents/카카오톡 받은 파일/API 명세서 최종.pdf`의 내 지원 목록·취소·팀 나가기 계약을 최신 서버와 대조했습니다.

기존 `.posts-layout`, `.posts-main`, `.post-panel`, `.post-chip`, Button, 공통 색상/폰트/여백을 재사용합니다. S12의 960px 콘텐츠 폭, 24px 카드 모서리, 28px 제목, 상태 칩, 수락자 연락처 안내 패널, 취소/팀 나가기 버튼을 구현했습니다. 확인한 S12는 이미지/아이콘 등 정적 이미지 자산이 없는 텍스트/도형 중심 화면이므로 새 이미지 자산은 필요하지 않습니다.

차이와 결정:

1. 디자인은 ‘대기’ 칩이 선택되어 있으면서 여러 상태 카드가 섞여 있습니다. 실제 API의 status 미지정은 전체 조회이므로 **전체** 필터를 추가하고 기본값으로 사용합니다. 대기/수락/거절/취소/마감/나감은 실제 서버 필터입니다.
2. 디자인의 예시 연락처 URL은 사용하지 않습니다. 수락 카드의 안내 패널에서 기존 모집글 상세의 연락처 기능으로 연결합니다. 목록에 연락처를 추가하거나 별도로 저장하지 않습니다. 삭제된 글은 상세/연락처 링크가 없습니다.
3. 총건수, 거절 사유, 팀장/프로필 ID, 글 모집 상태/숨김 여부는 응답에 없습니다. 임의로 표시하거나 프로필 링크를 만들지 않습니다. 지원일/처리일은 실제 제공 필드이므로 표시합니다. 나가기 시각은 별도 응답 필드가 없어 만들어 표시하지 않습니다.
4. 헤더의 예시 알림 수 3은 근거가 없어 표시하지 않고 기존 ‘알림 · 준비 중’을 유지합니다. 기존 헤더/다른 담당자의 마이페이지·프로필 화면은 유지합니다.
5. 로딩·에러/재시도·더 보기·갱신·인증 대기·명시적 개발 예시 선택창을 추가합니다. 원본과 완전히 일치한다고 주장하지 않습니다.
6. 760px 이하에서 카드 간격/패딩과 필터 터치 영역을 조정하고 440px 이하에서 제목/확인창을 조정합니다. 콘텐츠는 고정 위치 대신 가변 폭으로 배치합니다.

## API 계약 및 근거

| 항목 | 확인한 규칙 |
| --- | --- |
| 목록 | `GET /api/users/me/applications?size=20[&status=...][&cursor=...]`, 200 공통 envelope의 `data: {items,nextCursor,hasNext}` |
| 상태 | PENDING, ACCEPTED, REJECTED, CANCELED, CLOSED, LEFT. status 생략 시 전체. 단일 상태 필터. 잘못된 요청 상태는 400 |
| 순서/커서 | 지원 ID 내림차순. cursor는 이전 페이지 마지막 **지원 ID**이며 다음 조회는 id < cursor. 첫 요청은 cursor 생략. 서버 기본 size20, 1~50 보정. 화면은 실제 요청 size20 사용 |
| 페이지 종료 | hasNext 기준, nextCursor는 숫자 문자열/null. 화면에서는 문자열을 유지하고 잘못된/반복/진행하지 않는 커서 응답은 거절 |
| 항목 필드 | 지원 id/status/roleName, post.id/title/deleted, createdAt, decidedAt. 제목 링크에는 post.id, 처리 요청에는 지원 id 사용 |
| 지원 취소 | `PATCH /api/applications/{applicationId}/cancel`, 본문 없음. 본인 PENDING만. 200 data `{applicationId,status:CANCELED}` |
| 취소 경합 | 상태 확인 후 조건부 cancelPending으로 다시 검사. 수락/거절/이미 취소 등은 409 INVALID_APPLICATION_STATUS. 타인은403, 없는 지원404, 무인증401 |
| 재지원 | existsByPostIdAndApplicantId는 취소·거절·나감 포함 지원 이력 전체를 검사. 같은 글에 다시 지원 불가. 취소·나가기 확인창에 명시 |
| 취소 항목 | 팀장의 지원자 관리 목록에서는 제외되지만 본인의 전체/CANCELED 조회에는 남음. 성공 후 서버 목록 재조회로 필터별 포함 여부 결정 |
| 팀 나가기 | `PATCH /api/applications/{applicationId}/leave`, 본문 없음. ACCEPTED만. 200 data `{applicationId,status:LEFT,roleReopened,postReopened}`. S12에는 본인의 나가기만 표시. 수락된 지원에 cancel을 보내지 않음 |
| 나가기 후 | 자리를 반환하고 마감일 전에는 역할/글이 재개될 수 있음. 반환된 재개 값으로 목록 지원 상태를 바꾸거나 모집 상태를 추측하지 않음. 연락처 접근은 종료됨 |
| 인증 | 목록·취소·나가기는 로그인 필요. 작성/지원 POST의 VERIFIED 전용 조건을 임의로 추가하지 않음. 토큰 없음은 fetch 이전 차단 |
| 알 수 없는 응답 상태 | 원문을 ‘알 수 없는 지원 상태’로 표시하고 처리 버튼 없음. 대기/수락으로 치환하지 않음 |

읽은 근거:

- `backend/.../domain/application/ApplicationController.java`, `ApplicationService.java`, `ApplicationRepository.java`, `ApplicationStatus.java`
- `dto/MyApplicationResponse.java`, `CancelResponse.java`, `LeaveResponse.java`
- `backend/.../global/common/CursorPage.java`, `global/security/SecurityConfig.java`
- `backend/.../domain/post/PostService.java`의 상세 공개 정책
- 테스트 `ApplicationApiTest.myApplications`, `MyPostAndCancelApiTest.Cancel`, `TeamLeaveApiTest`를 읽었습니다. 백엔드 테스트 실행/백엔드 변경은 하지 않았습니다.

### 모집글 접근 정책

- 목록의 `findMine`은 삭제·숨김·마감 필터를 추가하지 않습니다. 지원 상태와 모집글 상태는 별개입니다. CLOSED는 지원 자체의 마감 결과이며, 마감 글에 수락된 지원도 ACCEPTED를 유지합니다.
- 삭제 글은 DTO에서 title=null, deleted=true이며 화면에서는 ‘삭제된 모집글’로 표시하고 상세 링크를 만들지 않습니다.
- 숨김 여부는 목록 응답에 없습니다. title로 숨김 여부를 추정하지 않고 상세로 연결합니다. 상세 API가 숨김404/삭제POST_DELETED/차단·정지 등의 접근 제한을 처리합니다. 마감된 글은 상세 접근 가능합니다.
- 지원 취소·팀 나가기 서비스는 지원 소유자/지원 상태를 검사하며 글의 마감/삭제 여부를 추가로 금지하지 않습니다. 따라서 목록의 PENDING은 취소, ACCEPTED는 나가기 동작을 제공합니다. 삭제 글이라는 이유로 다른 지원 상태로 바꾸지 않습니다.
- S12에는 팀장/프로필 식별 정보가 없어 별도 프로필 연결을 추가하지 않았습니다. 기존 상세 내 프로필 연결을 이용합니다.

## 상태·인증 구현

- 최초 로딩, 빈 목록, 전체 조회 실패/재시도, 추가 조회 실패/재시도, 더 보기 중복 잠금, 항목 ID 중복 제거를 처리합니다.
- URL `status`를 복원합니다. 잘못된 상태는 전체로 해석하고 필터 선택 시 cursor/size를 제거합니다. 커서는 화면 세션에서만 관리하며 URL 중간 페이지로 건너뛰지 않습니다.
- 사용자/토큰/필터/예시 조건이 바뀌면 페이지 scope key를 바꿔 기존 목록과 확인창을 즉시 제거합니다. dispose는 조회/처리 요청을 abort하고 늦은 결과를 무시합니다.
- 화면 복귀/재진입, 창 focus, pageshow, 탭 visibility 복귀 때 첫 페이지를 다시 조회합니다. 진행 중 처리와 겹치면 임의로 갱신하지 않습니다.
- 취소/나가기 전 네이티브 dialog로 확인합니다. 돌아가기 버튼/ESC를 제공하고 포커스를 복원합니다. 같은 tick 연속 요청도 controller가 1회로 제한합니다.
- 성공 결과는 먼저 확인된 상태로 보존하고 서버 첫 페이지를 재조회합니다. 성공 후 조회 실패는 별도로 알리고 처리를 잠급니다. 목록 재조회만 재시도할 수 있습니다. 늦은 PENDING/ACCEPTED 응답으로 이미 확인한 CANCELED/LEFT를 되돌리지 않습니다.
- 409 상태 변경·네트워크/잘못된 응답·서버 실패는 성공 안내를 하지 않고 먼저 재조회하도록 처리합니다. 401/403은 기존 목록을 숨깁니다.
- PR #37 미병합으로 공통 인증 공급 경로가 없습니다. `MyApplicationsPage`의 선택적 session `{userId,token}`을 기존 API client의 token 옵션으로 전달할 수 있게 준비했지만 실제 라우트에는 아직 공급하지 않습니다. 별도 인증/토큰 저장/가짜 헤더는 만들지 않았습니다. 연결 후 로그아웃·사용자 전환 때 해당 props를 즉시 교체해야 합니다.
- 상세의 인증 연동도 #37 병합 후 기존 방식에 맞춰 연결해야 하며 이번 작업에서 다른 담당자 인증/프로필 코드를 바꾸지 않았습니다.

## 변경 파일

- `src/pages/MyApplicationsPage.tsx`: 기존 `/me/applications` 경로의 실제 페이지, URL 필터/초기화, 개발 예시, 조회/처리 상태와 확인창.
- `src/features/my-applications/model.ts`: 응답 타입·상태·필터·행동 조건·오류/표시 규칙.
- `api.ts`: 실제 GET/cancel/leave, 인증 차단, endpoint 응답 검증.
- `controller.ts`: 조회/페이지/처리 경합·성공 후 재조회·dispose.
- `source.ts`: 실제/명시적 예시 서비스, 사용자별 예시 데이터와 오류 시나리오. structuredClone 사용 없음.
- `MyApplicationsList.tsx`: 카드·상태·날짜·연락처 상세 연결·액션 표시.
- `ApplicationActionDialog.tsx`: 지원 취소/팀 나가기 확인창.
- `my-applications.css`: S12 전용 반응형 스타일, 기존 모집 토큰 재사용.
- `src/layouts/AppLayout.tsx`: `/me/applications` 정확한 주소만 기존 모집 전용 레이아웃에 포함. 기존 모집 경로 판별 유지.
- `scripts/check-posts.mjs`: 임시 화면이던 내 지원 내역을 모집 레이아웃으로 검증하고 관계없는 하위 주소는 제외.
- `scripts/check-my-applications.mjs`: S12 전용 API/controller/정적 렌더 회귀 검증.
- `MY-APPLICATIONS.md`: 이 안내.

## 검증 및 한계

- build·lint 통과.
- S12 전용 검증 22개 통과: URL 필터, 커서, 상태/삭제 표시, cancel/leave 조건, 동시 요청, 지연 응답, 사용자 전환/인증 차단, 성공 후 조회 실패/지연 상태 보존, explicit 예시 및 structuredClone 부재.
- 기존 목록/상세 11, 작성 14, 지원 10, 지원자 관리 17, 마감/연락처 17, 검색/필터 14 검증 통과. 합계 **105개**.
- 검증은 fetch 가로채기·상태 제어·정적 React 렌더입니다. 실제 JWT·인증 사용자 전환·CORS·백엔드/DB 목록·취소/나가기·상세 연락처 권한 통합 통신은 미검증입니다.
- 기존 5173 개발 서버가 `VITE_POSTS_MOCK=true`로 응답함을 확인해 재사용했습니다. 새 서버를 실행하거나 기존 서버를 종료하지 않았습니다.
- 에이전트는 브라우저 연결 호스트 부재로 실제 PC/모바일 비교를 완료하지 못했습니다. 이후 **사용자가 S12 개발 예시의 화면·지원 취소·팀 나가기·상세 이동을 직접 확인했고 정상 동작함을 확인했습니다.** 이는 개발 예시 확인이며 실제 JWT·DB 통신 검증과 구분합니다. PC/모바일 각각의 확인이나 원본 디자인과의 완전 일치로 확대 해석하지 않습니다.

## 직접 확인 주소와 체크리스트

- 기본 전체 예시: http://127.0.0.1:5173/me/applications?example=true
- 수락 필터: http://127.0.0.1:5173/me/applications?example=true&status=ACCEPTED
- 대기 필터: http://127.0.0.1:5173/me/applications?example=true&status=PENDING
- 실제 인증 대기: http://127.0.0.1:5173/me/applications

개발 예시는 **개발 서버 + VITE_POSTS_MOCK=true + example=true**일 때만 적용합니다. 실제 API 실패가 예시 성공으로 바뀌지 않습니다. 실제 API/DB는 수정하지 않으며 예시 상태는 현재 탭 모듈 메모리만 사용합니다. 같은 사용자·시나리오는 필터 이동 시 유지되고 ‘예시 초기화’ 또는 브라우저 새로고침으로 초기화됩니다. 실제 데이터/토큰을 이 예시 저장소에 넣지 않습니다.

상단 예시 선택창에서 조회 상태/list, 처리 결과/result, 사용자/viewer를 변경할 수 있습니다.

| 조건 | 확인 |
| --- | --- |
| 기본 전체 | 첫 4개 카드와 더 보기, 6개 상태가 각각 원래 상태로 표시됨. 지원 ID와 글 ID가 다름 |
| status=PENDING | 지원 취소 → 돌아가기로 요청 없음 → 확인 시 취소 성공 후 대기 목록에서 제외 → 취소 필터에서 유지/재지원 불가 안내 |
| status=ACCEPTED | 팀 나가기 확인 → LEFT, 수락 필터에서 제외 → 나감 필터에 표시. 지원 취소 버튼 없음 |
| result=changed | 대기 취소 직전 수락된 상태를 모사하여409 → 성공 문구 없음/추가 처리 잠금 → 재조회 후 수락과 팀 나가기 표시 |
| result=failure / network | 서버 실패/서버 처리 후 응답 유실 → 목록 재조회 전 중복 처리 없음. network는 재조회 후 CANCELED/LEFT 확인 |
| result=refreshFailure | 성공 문구와 최신 조회 실패를 구분 → 처리 잠금 → 목록 다시 조회만으로 복구, mutation 재전송 없음 |
| result=expired / forbidden / noAuth | 인증 만료/권한 없음/토큰 없음 → 목록 숨김 또는 요청 차단, 재조회 안내 |
| list=empty / error / moreError / slow | 빈 목록/초기 조회 실패/추가 페이지 실패/느린 조회 중 필터 변경 시 이전 응답 무시 |
| list=restrictedPosts | 마감 글에도 수락 유지, 삭제 글 상세 링크 없음, 숨김 예시는 상세 접근 실패(예시 id999) |
| list=unknown | 알 수 없는 상태 표시, 취소/나가기 버튼 없음, 임의 대기·수락 처리 없음 |
| viewer=bob / guest | 사용자 A 목록/확인창 폐기 후 B 목록 또는 로그아웃 안내. 전환 전 느린 조회가 도착해도 이전 사용자 목록이 나타나지 않음 |

추가 확인: 제목으로 상세 이동 후 뒤로 돌아오기/다른 탭으로 이동 후 복귀 시 최신 조회, 수락 카드의 연락 수단 안내가 기존 상세로 연결되는지, PC 1440px와 모바일 390px에서 카드/필터/확인창 줄바꿈·가로 넘침·키보드 포커스를 확인해 주세요.

## PR 준비 시 재확인

- `git fetch origin` 후 최신 dev는 `2b9d2e2`이며 HEAD와 차이 0/0입니다. dev 반영/충돌 해결은 필요하지 않습니다. PR #37·#38·#39는 모두 미병합이며 다른 기능의 변경을 가져오지 않았습니다.
- PR diff에는 S12 페이지·전용 모듈·관련 검증·이 문서와 S12에 필요한 공통 레이아웃 한 줄만 포함합니다. POST-02 라우트/수정 폼/레이아웃 변경은 포함하지 않습니다.
- 사용자 개발 예시 화면·취소·나가기·상세 이동 확인 완료를 기록하며 실제 JWT·DB 통신은 미검증으로 남깁니다.
- 전체 필터 추가, 별도 연락처 노출 대신 기존 상세 연결, 제공되지 않는 총건수/거절 사유/모집글 상태 미표시 등의 디자인/API 차이는 위 결정사항을 유지합니다.
- 남은 작업은 공통 인증 session 공급 및 상세 인증 연결, 실제 JWT·DB·CORS 통합 검증, PC/모바일 각각의 시각/키보드 접근성 비교입니다. 인증 연결 전에는 무토큰 요청을 차단합니다.
- S12 관련 파일만 명시적으로 스테이징하며 환경 파일·비밀값·로그·임시 파일은 제외합니다. 강제 푸시·원격 PR 병합·다음 기능 개발은 하지 않습니다.
