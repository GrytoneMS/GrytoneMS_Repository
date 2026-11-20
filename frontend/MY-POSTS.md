# 내 모집글 S11 · APPL-11

## 기준과 범위

2026-11-15 README와 깨끗한 작업 트리를 확인했습니다. fetch 후 PR #34·#36 모두 MERGED, 최신 origin/dev `2b9d2e2`에서 기존에 없던 `feature/front-APPL-11`을 생성했습니다. 기존 POST-04 브랜치와 structuredClone 호환성 수정은 보존했습니다. 구현 후 사용자 확인을 거쳐 커밋·일반 푸시·dev 대상 PR 생성 범위로 진행합니다. PR 병합과 다음 기능 개발은 포함하지 않습니다. 최종 fetch에서도 origin/dev는 `2b9d2e2`로 같아 추가 통합할 dev 커밋은 없었습니다.

읽기 전용 참고 자료:

- `C:/Users/user/Downloads/멋사 그려톤_팀 모임 웹사이트/S11 · 내 모집글.pdf`
- `C:/Users/user/Downloads/멋사 그려톤_팀 모임 웹사이트/Spec · S11 · 내 모집글.pdf`

두 PDF의 전체 페이지를 텍스트 추출하고 이미지로 렌더링해 확인했습니다. Figma 호출은 하지 않았습니다. 중앙 864px 목록, 밝은 배경, 둥근 흰 카드와 역할 행, 파랑 지원/초록 수락 배지, 우측 카드 동작 배치를 기존 Button·모집 화면 토큰으로 구현했습니다. 모바일은 제목·역할명·배지·버튼이 줄바꿈하도록 구성했습니다. 공통에 없는 배지 색상은 화면 전용 CSS 변수로 정의했습니다. 예시 제목/지원 수/알림 숫자 3을 실제 데이터로 넣지 않았고 서버가 제공하지 않는 전체 건수도 표시하지 않습니다.

backend·인증·프로필·내 지원 내역·환경 파일·의존성·lockfile은 수정하지 않았습니다. 공통 변경은 `/me/posts`의 기존 모집 레이아웃 사용 및 마이페이지 활성 표시, 기존 마감 확인창 export/최소 prop 타입으로 제한했습니다.

## 최신 백엔드 계약과 PDF 차이

- `MyPostController`, `MyPostService`, `MyPostResponse`, `CursorPage`, `PostRepository.findMine`, `MyPostAndCancelApiTest`를 확인했습니다.
- GET `/api/users/me/posts?status=OPEN|CLOSED&size=20&cursor=...`. status 기본 화면은 OPEN. 서버에서는 status 생략 시 전체이나 UI는 항상 탭 상태를 보냅니다.
- 최신 id 내림차순. cursor는 마지막 글 id의 문자열이며 Java Long 범위를 원문으로 전달합니다. 기본 size 20, 서버 허용 범위 1~50. 응답은 `{ success: true, data: { items, nextCursor, hasNext } }`. 총 건수는 없습니다. 숨김/삭제/다른 사람 글은 제외됩니다.
- 역할별 ‘지원 N’은 applicantCount, ‘수락 N/정원’은 filledCount/headcount입니다. pendingCount는 대기 지원 수이며 전체 지원자 수로 쓰지 않습니다. dDay와 status도 응답값을 사용합니다.
- **삭제 조건 대조:** 현재 `PostService.deletePost`는 `countByRoleAndStatus` 중 CANCELED가 아닌 지원이 존재하는지 검사합니다. `PostDeleteApiTest.ByLeader.applicants`는 대기/거절 지원이 있으면 거절하고 취소만 있으면 삭제 성공을 검증합니다. 따라서 현재 서버는 ‘취소 이력까지 있으면 무조건 삭제 불가’가 아니며, 목록 applicantCount와 동일하게 취소자를 제외합니다. 역할 자체를 제거하는 POST-02의 `updateRoles`는 취소 이력도 검사하므로 글 삭제와 혼동하면 안 됩니다.
- 지원 수가 0이면 삭제 후보 버튼을 제공하지만 최종 권한·동시 지원·삭제 가능 여부는 DELETE 서버가 판단합니다. 지원자가 있는 OPEN 글에는 PDF의 마감 대안 문구, CLOSED 글에는 ‘지원자가 있어 삭제할 수 없어요.’를 표시합니다.
- DELETE `/api/posts/{id}` → HTTP 200, data:null. POST_HAS_APPLICANTS(409), 401/403/404 및 형식/통신 오류를 표시합니다. 서버 오류를 성공으로 바꾸지 않습니다.
- 수동 마감은 기존 closePost(PATCH `/api/posts/{id}/close`, data:null), ClosePostDialog, actionError를 재사용합니다. 연락처 조회 UI와 요청은 목록에 추가하지 않았습니다.
- PDF의 수정 버튼은 요구된 범위에 따라 ‘수정 · 준비 중’ 비활성입니다. 작성 화면으로 연결하지 않았습니다. **따라서 최종 S11 전체 완성이 아니라 POST-02 수정 화면이 남은 상태입니다.**

## 상태·요청 처리

URL의 status로 탭을 보존합니다. 탭 변경 시 콘텐츠를 교체해 목록/커서를 초기화하고 이전 컨트롤러를 dispose/abort합니다. 브라우저 뒤로가기 시 URL 탭을 복원하고 새 목록을 조회합니다. 상세/지원자 관리로 이동했다가 브라우저 뒤로 돌아와도 컴포넌트가 다시 마운트되며 재조회합니다. 목록 페이지 번호를 URL에 보존하거나 총 건수를 추정하지 않습니다.

첫 조회 로딩·빈 목록·오류/재시도·더 보기 로딩/오류/재시도를 제공합니다. 더 보기 실패는 기존 목록을 유지하고 같은 틱 중복 요청과 중복 id를 막습니다. 세대 검사와 abort로 오래된 조회·오류 및 화면 이탈 후 응답을 무시합니다.

마감/삭제 전 확인창, 요청 중 닫기/중복 처리 차단, 성공 후 현재 탭 첫 페이지 재조회를 제공합니다. 성공한 항목은 즉시 현재 목록에서 제거하고, 서버 재조회 실패를 처리 실패와 구분합니다. 확인된 마감/삭제 항목이 지연된 목록에 다시 등장하면 성공 상태를 되돌리지 않고 목록 재조회를 안내합니다. 결과 불명확/서버 거절은 성공 안내 없이 최신 목록 조회 전 처리를 잠급니다. 조회 후 새 데이터로 재시도할 수 있습니다. 이미 CLOSED인 글은 수동 마감 불가입니다.

## 인증과 개발 예시

최신 dev에는 공통 인증 컨텍스트가 없습니다. 별도 로그인·토큰 저장을 만들지 않았으며 실제 UI는 무토큰 조회·마감·삭제를 fetch 전에 차단합니다. 향후 공통 인증 세션에서 `myPostsService(query, { token })`에 토큰을 전달하고 사용자/토큰 변경 시 서비스와 화면을 교체해야 합니다. 실제 UI 인증 연결, JWT·학교 인증·CORS·실제 서버/DB 성공·실패는 미검증입니다.

개발 mock은 기존 `DEV && VITE_POSTS_MOCK === 'true'`에서만 동적 로딩합니다. 실제 API 실패 후 mock으로 전환하지 않습니다. structuredClone을 사용하지 않고 DTO 중첩 객체/배열을 직접 복사합니다. mock의 처리는 이 화면의 메모리이며 탭 사이에는 유지되고 화면 이탈/새로고침/예시 조건 변경 시 초기화됩니다. 상세/지원자 관리 mock과 공유하는 DB는 아닙니다. 더 보기 검증을 위해 예시 페이지 크기는 3이며 모집중 예시 수는 서버의 실제 등록 제한을 나타내지 않습니다.

기존 5173 리스너(PID 25332)를 재사용했고 실제 제공 모듈의 DEV=true, VITE_POSTS_MOCK=true를 확인했습니다. 새 개발 서버·백엔드는 실행하지 않았습니다. 재실행이 필요한 경우 기존 서버 터미널에서 Ctrl+C 후 frontend에서 Git Bash 기준:

```bash
VITE_POSTS_MOCK=true npm run dev -- --force --host 127.0.0.1 --port 5173 --strictPort
```

| 주소 (http://127.0.0.1:5173 뒤에 붙임) | 확인 항목 |
| --- | --- |
| `/me/posts` | 모집중·역할별 지원/수락 수·삭제 조건·더 보기 |
| `/me/posts?status=CLOSED` | 마감 탭·마감 중복 차단·지원자 있음/없음 |
| `/me/posts?mine=empty` | 빈 목록 |
| `/me/posts?mine=error` | 조회 실패 |
| `/me/posts?mine=slow` | 로딩·탭 전환 |
| `/me/posts?mine=moreError` | 더 보기 실패·기존 목록 유지 |
| `/me/posts?result=failure` | 마감·삭제 서버 실패 |
| `/me/posts?result=network` | 처리 응답 불명확·재조회 전 잠금 |
| `/me/posts?result=refreshFailure` | 마감·삭제 성공 후 목록 재조회 실패 |
| `/me/posts?result=hasApplicants` | 삭제 확정 시 서버 지원자 존재 거절 |

지원자 없는 카드에서 삭제를, 모집중 카드에서 수동 마감을 선택해 확인합니다. 실패 후에는 확인창을 닫고 목록 다시 조회를 사용할 수 있습니다. 예시 선택 패널로 정상 상태로 복구할 수 있습니다.

## 검증과 남은 확인

frontend에서 build·lint 경고 없이 통과. 아래 검증은 API 가로채기·컨트롤러·정적 렌더·명시적 mock이며 실제 서버/DB/브라우저 테스트가 아닙니다.

| 명령 | 결과 |
| --- | --- |
| `node scripts/check-my-posts.mjs` | 14개 통과 |
| `node scripts/check-applicants.mjs` | 17개 통과 |
| `node scripts/check-post-actions.mjs --mock` | 17개 통과 |
| `node scripts/check-applications.mjs --mock` | 11개 통과 |
| `node scripts/check-posts.mjs` | 11개 통과 |
| `node scripts/check-post-filters.mjs` | 14개 통과 |
| `node scripts/check-post-create.mjs` | 14개 통과 |

총 98개. 새 검증에는 OPEN/CLOSED/size/Long cursor/Bearer/signal, 무토큰 fetch 0회, 응답 검증, 오래된 요청 폐기, 더 보기 중복·재시도, 삭제 조건, 같은 틱 중복 처리, 마감/삭제 후 조회, 성공 후 갱신 실패·stale 목록, 권한/지원자 존재 오류, 폐기 후 응답 무시, 카드 정적 렌더, structuredClone 부재를 포함합니다. `/me/posts`가 새 레이아웃을 사용함에 따라 기존 check-posts의 관련 기대값만 갱신했습니다. git diff --check와 충돌 표시 검사도 수행합니다.

사용자가 내 모집글 개발 예시 화면을 직접 확인했고 정상 동작한다고 알려 **사용자 개발 예시 화면 확인 완료**로 기록합니다. 세부 상태·뷰포트·접근성 항목별 확인 범위는 별도 보고되지 않았으므로 모든 수동 체크리스트를 개별 검증했다고 간주하지 않습니다. 에이전트의 직접 PC·모바일 렌더·클릭·반응형·키보드/포커스 확인은 미완료입니다. 브라우저 연결은 host unavailable, Edge native 창은 최소화 상태로 제어 도구가 캡처하지 못했습니다. HTTP 모듈 확인과 정적 렌더를 실제 브라우저 검증이라고 기록하지 않습니다. 개발 예시 확인은 실제 백엔드 인증·통신·JWT·CORS·DB 검증과 구분하며 후자는 미검증입니다.

수동 재확인: 1440px/768px/375px/320px에서 긴 제목/역할명/버튼 줄바꿈, 탭 복원과 상세/관리에서 뒤로 복귀 후 재조회, 더 보기, 확인창 취소/ESC/Tab/포커스 복귀, 마감/삭제 중 중복 클릭 차단, 성공·실패·재조회 실패 구분, 마감 탭 삭제 불가 문구를 확인하세요.

## 다음 POST-02 수정 화면 준비 사항 (이번에는 구현하지 않음)

- PUT `/api/posts/{id}`, 본문은 서버 PostCreateRequest, 성공 응답은 PostDetailResponse입니다. 역할 요청에는 선택적 id가 있어 기존 역할 유지/수정과 새 역할을 구분합니다.
- PostUpdateApiTest와 PostService.updateRoles 기준: 지원자(취소 제외)가 있으면 역할 종류 변경/인원 축소가 제한되고, 역할 삭제는 취소 기록도 포함해 지원 이력이 하나라도 있으면 제한됩니다. 수락자에게 연락 링크 변경 알림도 발생합니다.
- 기존 `PostCreatePage.tsx`, `create-model.ts`의 PostDraft/검증/직렬화, `create.css`, Button/Input을 재사용할 수 있습니다. 현재 작성용 직렬화는 역할 id를 보내지 않으므로 수정에서는 기존 id 보존·폼 초기화·업데이트 전송을 별도로 설계해야 합니다.
- 일반 상세 응답에 contactLink가 없으므로 수정 폼의 연락 링크 초기값을 추측하거나 빈 값으로 덮어쓰면 안 됩니다. 팀장 권한의 기존 별도 연락 수단 API와 수정 폼 초기화 정책을 정해야 합니다.
- 인증 연동과 실제 통신 검증 및 POST-02 구현 후 S11 최종 완료 여부를 다시 판단해야 합니다.
