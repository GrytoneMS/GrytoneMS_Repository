# S08 지원자 관리 · APPL-04/05/06

## 작업 기준과 범위

- `git fetch origin` 후 최신 `origin/dev` (`8093aac`)에서 `feature/front-APPL-04` 생성. 시작 시 `feature/front-APPL-01` 작업 트리는 깨끗했고 기존 브랜치·커밋을 보존함.
- PR [#33](https://github.com/GrytoneMS/GrytoneMS_Repository/pull/33)은 OPEN·미병합으로 확인함. 지원 모달 변경을 merge/cherry-pick하지 않음.
- 서버 Controller/DTO 주석에서 지원자 조회는 **APPL-04**, 수락은 **APPL-05**, 거절은 **APPL-06**으로 확인함. APPL-02는 지원자 프로필 기능이라 요청한 브랜치 이름을 대표 조회 기능 ID에 맞춰 조정함.
- Figma [S08 `8:419`](https://www.figma.com/design/TQAHXMAyHdCqU5aV3qBmhW/?node-id=8-419)의 디자인 컨텍스트·스크린샷을 읽기 전용 확인. 역할 선택·지원자 카드·거절 사유·팀 현황을 기존 React/CSS와 Button, 상태 배지, 색상 변수로 구성함.
- 저장소에 독립 API/기능 명세서 원문이 없어 기존 문서의 1차 명세 참조 및 최신 `ApplicationController`, `RoleApplicantsResponse`, `AcceptResponse`, `RejectRequest`, `RejectResponse`, `RejectReason`, `ApplicationStatus`, `ApplicationService`, `ApplicationRepository`, `RoleSeatRepository`, `SecurityConfig`, `ErrorCode`를 대조함. 독립 원문과의 대조는 미완료.
- 범위: `/posts/:postId/applicants`, 본인 상세의 관리 링크, 역할별 지원자 조회·수락·거절, 확인창·상태·오류 처리, 개발 예시와 검증.
- 모집글 마감 API, 연락처 공개/수정, 팀원 내보내기, 지원 취소, 프로필 조회/수정은 구현하지 않음. 디자인의 기술·인증 배지·후기/경력은 조회 DTO에 없어 만들지 않음. 공개된 기본 정보·메시지·가능 시간만 표시하고 프로필은 기존 `/users/:id`로 연결함.
- backend·인증·프로필·내 모집글·내 지원 내역 화면과 의존성·환경 파일은 변경하지 않음. DB 삽입 없음. 사용자 예시 화면 확인 후 커밋·푸시·dev 대상 PR 생성 진행. PR 병합과 다음 기능 개발은 범위 밖임.

## API 계약과 처리 규칙

| API | 입력·응답 |
| --- | --- |
| `GET /api/posts/{postId}` | 인증된 본인 상세·모집 현황을 확인하며 `isMine` 검사 |
| `GET /api/posts/{postId}/applications` | `[{ postRoleId, roleName, headcount, filledCount, applications: [{ id, status, message, availableTime, createdAt, applicant: { id, nickname, schoolName, major, grade, profileImageUrl } }] }]` |
| `PATCH /api/applications/{applicationId}/accept` | 본문 없음. `{ applicationId, status: "ACCEPTED", roleClosed, postClosed }` |
| `PATCH /api/applications/{applicationId}/reject` | `{ reason: null 또는 "ROLE_MISMATCH"/"SCHEDULE"/"FILLED"/"ETC" }`. 응답 `{ applicationId, status: "REJECTED" }` |

모든 응답은 공통 `{ success, data }` envelope를 사용하며 공통 `apiRequest`로 처리함. 역할 조회 ID와 수락/거절의 지원 ID를 구분함. 응답 id·상태·인원·중복·DTO 구조를 검사하고 잘못된 성공 응답을 성공으로 처리하지 않음. 상세와 목록의 역할/인원이 어긋나면 재조회하도록 안내함.

- 조회는 팀장만 가능. 서버는 취소(CANCELED)한 지원을 제외하고 역할별·지원 시간순으로 반환함. 화면의 지원 수는 반환된 지원 건수이며 취소를 포함한 전체 지원 건수로 해석하지 않음.
- **수락:** 팀장, PENDING, 역할 OPEN, `filledCount < headcount` 조건. 서버가 조건부 UPDATE로 정원을 보호함. 현재 서버 accept는 글의 OPEN 여부나 마감일을 별도로 검사하지 않아 프론트에도 그 조건을 임의 추가하지 않음.
- 정원 도달 시 역할 자동 마감과 같은 역할의 남은 PENDING → CLOSED. 모든 역할이 닫히면 글도 자동 마감됨. 이 자동 처리 결과만 표시하며 직접 모집 마감 기능은 없음.
- **거절:** 팀장·PENDING 조건, 사유 선택. 글/역할 마감이나 정원 도달만으로 PENDING 거절을 임의 차단하지 않음.
- ACCEPTED·REJECTED·CANCELED·CLOSED·LEFT는 처리 버튼 비활성화. 상태를 임의로 되돌리거나 재지원·재수락 경로를 만들지 않음.
- 401/403/404는 로그인 만료·팀장 권한 없음·대상 없음으로 안내하고 기존 지원자 정보도 숨김. ROLE_FULL·ROLE_CLOSED·INVALID_APPLICATION_STATUS는 최신 현황 재조회를 요구함. 실제 API 오류를 mock 성공으로 바꾸지 않음.

## 상태·접근성 처리

- 수락·거절 전에 대상·역할·사유를 확인하는 네이티브 dialog 사용. 처음에는 취소 버튼에 포커스. Tab 순환·ESC 취소·닫은 후 포커스 복귀·배경 스크롤 잠금, 결과/오류 포커스 안내 처리.
- 요청 중 전체 처리 버튼과 예시 변경을 잠그고 동일 틱 중복 요청도 controller에서 차단함. 조회 도중에는 처리를 막아 오래된 데이터와 변경 요청이 경합하지 않게 함.
- 성공 응답을 받은 후 지원 상태·선발 인원·서버가 알려준 자동 마감을 먼저 반영하고 목록과 모집 현황을 재조회함.
- **처리 성공 후 조회만 실패**하면 성공 안내·확인된 결과를 유지하며 추가 처리를 잠금. 갱신 버튼은 조회만 재시도하며 수락/거절 요청을 다시 보내지 않음. 지연된 조회의 PENDING이 확인된 완료 상태를 덮어쓰지 못함.
- 네트워크/5xx/잘못된 응답은 처리됐을 가능성을 배제하지 않고 재조회 전까지 잠금. 권한 상실 시 민감한 기존 목록을 숨김. 화면 이탈·새 조회 이후 늦은 결과는 무시함.
- PC는 카드/오른쪽 현황 2열, 760px 이하에서는 1열. 긴 메시지와 이름 줄바꿈, 좁은 화면 버튼과 확인창 스크롤을 지원함.
- 지원자 이미지는 API의 동적 이미지와 기존 `public/posts/avatar.svg` 대체 이미지를 사용함. 디자인의 가짜 인물 이미지를 실제 사용자 정보처럼 고정하지 않음. 새 정적 이미지 없음.

## 인증 연결 대기

현재 dev에 공통 인증 컨텍스트가 없으며 로그인·학교 인증은 자리 표시 화면임. 별도 로그인/토큰 저장 구조·가짜 토큰·사용자 ID를 추가하지 않음.

- 실제 화면의 관리 조회·수락·거절은 토큰이 없어 **fetch 전 차단**하고 인증 연동 대기를 안내함.
- `managementService(postId, query, { token })`에 향후 공통 컨텍스트의 토큰을 공급하면 상세와 목록 조회·처리가 같은 인증으로 연결됨. 현재 컴포넌트는 토큰을 공급하지 않음.
- 화면의 `isMine` 검사와 별도로 실제 서버 401/403/404를 처리함. 서버가 최종 권한과 동시성 규칙을 집행함.
- 실제 JWT·CORS·팀장 API 조회·수락·거절 성공은 **미검증**. 자동 검증의 가로챈 fetch 및 개발 예시 결과와 구분함.

## 직접 확인 주소와 모드

기존 5173 개발 서버(PID 5456)를 재사용함. 새 서버/백엔드를 실행하지 않았음. 기존 서버의 `VITE_POSTS_MOCK=true`를 확인했고 관리 주소의 HTTP 200 응답을 확인함. HTTP 200은 렌더 검증을 의미하지 않음.

기본 확인 주소: **http://127.0.0.1:5173/posts/1/applicants?viewer=owner**

개발 예시는 기존 `import.meta.env.DEV && VITE_POSTS_MOCK === 'true'`일 때만 별도 동적 모듈로 동작함. 실제 모드의 viewer/manage/result 쿼리는 인증·응답·결과에 영향을 주지 않음. 예시 상태는 화면 생명주기 안에서만 유지되며 새로고침/예시 조건 변경 시 초기화됨. 실제 API/DB에 저장하지 않음.

| 주소 (`http://127.0.0.1:5173` 뒤에 붙임) | 확인 내용 |
| --- | --- |
| `/posts/1?viewer=owner` | 본인 상세 → 지원자 관리 링크 |
| `/posts/1/applicants?viewer=owner` | 여러 역할·PENDING/ACCEPTED/REJECTED/CLOSED/LEFT·선발 인원 |
| `/posts/1/applicants?viewer=other` | 팀장 권한 없음 |
| `/posts/1/applicants?viewer=guest` | 로그인 필요 |
| `/posts/1/applicants?manage=empty` | 역할별 지원자 없음 |
| `/posts/1/applicants?manage=slow` | 느린 로딩 |
| `/posts/1/applicants?manage=error` | 조회 오류·재시도 |
| `/posts/1/applicants?manage=notFound` | 404 대상 없음 |
| `/posts/1/applicants?manage=full` | 첫 역할 마감/정원 도달, 대기 지원은 거절만 가능 |
| `/posts/1/applicants?manage=closedPost` | 글만 마감된 방어 예시: 역할 OPEN/PENDING이면 서버 조건에 따른 수락·거절 허용 |
| `/posts/1/applicants?manage=canceled` | CANCELED 방어 표시. 실제 현재 조회에는 포함되지 않음 |
| `/posts/1/applicants?result=success` | 수락/거절 후 목록·선발 인원 갱신 |
| `/posts/1/applicants?result=refreshFailure` | 처리 성공 후 목록 재조회만 실패·완료 상태 보존 |
| `/posts/1/applicants?result=failure` | 서버 처리 오류 |
| `/posts/1/applicants?result=full` | 동시 정원 초과 ROLE_FULL |
| `/posts/1/applicants?result=processed` | 이미 처리된 지원 |
| `/posts/1/applicants?result=expired` | 401 |
| `/posts/1/applicants?result=forbidden` | 403 |
| `/posts/1/applicants?result=notFound` | 404 |
| `/posts/1/applicants?result=network` | 결과를 확인하지 못한 요청·재조회 전 잠금 |

상단 개발 예시 선택 메뉴에서도 전환 가능. `viewer` 생략 시 이 관리 예시 안에서만 팀장으로 시작함. 성공 후 재조회 실패 예시는 계속 조회 실패함. 예시 조건을 정상으로 바꾸면 저장소 전체가 초기화되는 점에 유의.

모드 전환은 기존 개발 서버를 종료한 뒤 같은 터미널에서 환경 변수를 변경하고 다시 실행함. 서버를 중복 실행하지 말 것. `.env`는 이번 작업에서 변경하지 않았음.

```powershell
cd frontend
$env:VITE_POSTS_MOCK='true'  # 예시. 실제 API 모드는 'false'
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

## 검증 결과

2026-11-13 기준:

- `npm.cmd run build`, `npm.cmd run lint` 통과.
- `node scripts/check-applicants.mjs`: 15개 통과. 토큰 없는 요청 0회, 정확한 id/사유/인증, 권한, DTO, 6가지 상태별 버튼, 서버 마감/정원 조건, 중복 요청, 성공 후 갱신, 재조회 실패 분리, 늦은 응답/화면 이탈, 정적 렌더, 개발 예시 성공/실패 검증.
- `node scripts/check-posts.mjs`: 11개 통과. 관리 경로를 새 모집 레이아웃으로 기대하도록 관련 정적 렌더 검사만 갱신함.
- `node scripts/check-post-create.mjs`: 14개 통과.
- `node scripts/check-post-filters.mjs`: 14개 통과.
- 자동 검증은 총 **54개 통과**(지원자 관리 15 + 목록·상세 11 + 작성 14 + 검색·필터 14).
- **사용자 예시 화면 확인 완료**: 사용자가 개발용 예시의 지원자 관리 화면을 직접 확인했고 문제없었다고 알려 확인 완료로 기록함. 세부 기기·뷰포트·항목별 범위는 별도 보고되지 않았으므로 아래 체크리스트 전체를 개별 확인했다고 간주하지 않음.
- 실제 API/DB 검증은 수행하지 않았음. 위 결과는 요청 가로채기·상태 전이·정적 렌더·개발 mock 결과임.
- 인증 연동 부재로 **실제 백엔드 조회·수락·거절 성공은 미검증**임. 사용자 예시 화면 확인을 실제 서버 성공 검증으로 기록하지 않음.
- 에이전트의 브라우저 연결은 `The browser access host is unavailable`로 실패해 반복하지 않았음. 아래 목록은 추후 세부 재확인용 체크리스트로 유지함.

수동 확인 체크리스트:

- 본인 상세 → 관리 화면 이동·상세 복귀. 다른 사용자/비회원에서 지원자 정보가 노출되지 않는지.
- 역할별 버튼을 Tab/Enter로 선택하고 카드·지원 수·선발/모집 인원·프로필 링크 id 확인.
- 사유 선택/선택 해제 → 거절 확인창의 대상·사유 확인. 취소/ESC 시 요청 없음·포커스 복귀.
- 수락 확인 → 처리 중 연속 클릭/Enter 방지 → 해당 지원 수락·선발 인원 증가·동일 역할 대기 마감 확인.
- 거절 확인 → 해당 지원 거절·선발 인원 불변 확인.
- 오류·이미 처리·정원 초과 → 성공으로 바뀌지 않고 최신 목록 갱신으로 안내되는지.
- 성공 후 조회 실패에서 완료 안내·현재 상태 유지와 모든 처리 버튼 잠금, 조회만 재시도 확인.
- 320/375/768/1440px에서 긴 이름/지원 내용, 확인창, 버튼, 프로필 이동 및 키보드 포커스 확인.
- 실제 모드에서 관리 페이지 접근 시 인증 연동 대기 안내·Network에 관리 GET/PATCH 요청 없음 확인.
- 기존 목록·검색·필터·상세·작성 화면과 인증/프로필/내 활동 자리 표시 화면 유지 확인.

## 공통 변경과 통합 주의

공통 파일 변경은 `AppLayout.tsx`의 경로 판정 한 줄뿐임. 관리 경로에도 기존 모집 화면 헤더·스타일 변수·반응형 틀을 적용하기 위해 필요함. App 라우트, 공통 컴포넌트/API/타입/토큰·패키지는 변경하지 않음.

`PostDetailPage.tsx`는 본인 글 버튼을 관리 링크로 바꾸고 안내 문구만 갱신함. PR #33도 같은 버튼/안내를 수정하므로 추후 통합 시 지원 모달 동작과 본인 관리 링크를 함께 보존해야 함. 이번 브랜치에는 PR #33의 모달 코드를 가져오지 않았음.
