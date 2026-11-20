# 스크랩 등록·해제 및 내 스크랩 (SCRP-01·02)

## 기준과 범위

- README의 `feature/front-기능ID` 규칙에 따라 `feature/front-SCRP-01`을 최신 `origin/dev=479d94d`에서 생성했습니다. 작업 시작 시 #37(인증·프로필), #41(로고)은 OPEN이어서 가져오지 않았습니다. 기존 브랜치와 로고 stash 두 개를 보존했습니다.
- 최신 ScrapController/DTO/Service/Repository, PostCardResponse/PostDetailResponse, SecurityConfig, CursorPage 및 `ScrapApiTest`를 읽고 외부 `API 명세서 최종.pdf`의 스크랩 항목과 대조했습니다. backend·DB는 변경하지 않았습니다.
- Figma 파일 `TQAHXMAyHdCqU5aV3qBmhW`를 읽기 전용 세션으로 조회했으나 Starter 호출 한도에 걸렸습니다. 제공 폴더의 `S13 · 스크랩.pdf`, `Spec · S13 · 스크랩.pdf`를 텍스트 추출·이미지 렌더로 확인했습니다.
- S13의 3열 카드·모집중/마감·파란 북마크를 기존 PostCard/배지/버튼/모집 레이아웃으로 구현했습니다. 기존 목록·상세 북마크의 Figma 원본 및 모바일 전용 디자인과의 직접 대조는 미완료입니다. 북마크는 로컬 SVG 마크업이며 임시 Figma URL/래스터 화면 이미지를 사용하지 않습니다. 미제공 알림 수나 총 스크랩 수는 만들지 않았습니다.

## API 계약

| 동작 | 경로/메서드 | 응답·권한 |
| --- | --- | --- |
| 등록 | `PUT /api/posts/{postId}/scrap` | `{postId,isScrapped:true}`. 학교 인증 회원만, 본인 글 400, 없는/숨김/삭제/정지 작성자 글 404. 마감 글도 등록 가능 |
| 해제 | `DELETE /api/posts/{postId}/scrap` | `{postId,isScrapped:false}`. 로그인만 필요. 없는 스크랩·숨김·삭제 글도 멱등 성공 |
| 내 목록 | `GET /api/users/me/scraps?size=20&cursor=...` | 로그인 필요. `{items:[{scrapId,scrappedAt,post:PostCardResponse}],nextCursor,hasNext}` |

- 최초 cursor 생략, 다음 cursor는 마지막 **스크랩 ID**의 Long 문자열이며 모집글 ID와 다릅니다. 문자열 원문을 유지하며 실제 요청은 size=20, 서버 기본 20/최대 50입니다.
- 최신 스크랩 ID 내림차순. 삭제·숨김·차단 관계·정지 작성자 글은 서버 쿼리에서 제외합니다. 클라이언트에 deleted/hidden 가짜 필드를 추가하지 않았습니다.
- 공통 `apiRequest`와 명시적 token/signal 옵션을 사용합니다. 성공 envelope·응답 ID/상태·중복/잘못된 페이지 커서 등을 검증하고 오류를 mock 성공으로 바꾸지 않습니다. 안전 정수 범위를 벗어난 숫자 ID 응답은 손상된 ID로 사용하지 않고 거부합니다. cursor 자체는 Long 최댓값까지 문자열로 전달합니다.

## 상태·인증 처리

- 카드와 상세는 서버의 `isScrapped`를 초기값으로 사용합니다. 성공 응답을 페이지 간 공유하며 새 서버 스냅샷도 반영합니다. 처리 전에 시작한 오래된 목록/상세 응답은 성공 상태를 덮어쓰지 못합니다.
- 북마크 버튼은 카드 링크의 **형제 요소**입니다. 클릭 시 preventDefault/stopPropagation을 함께 처리하며 링크 안에 버튼을 중첩하지 않습니다. aria-pressed/접근 가능한 이름, 요청 잠금 및 카드별 오류 안내를 제공합니다.
- 본인 글 등록은 확인 가능한 사용자 ID/상세 isMine으로 막고, 서버 거절도 처리합니다. 등록에만 학교 인증이 필요하며 해제에 같은 제한을 추가하지 않았습니다. 사용자 ID가 없는 목록에서 소유 여부를 추정하지 않습니다.
- 같은 tick 중복 요청 차단. 실패는 기존 표시 유지. 네트워크/잘못된 응답으로 결과가 불명확하면 재처리를 잠그고 `스크랩 상태 다시 조회`로 상세의 isScrapped만 재조회합니다. PUT/DELETE를 반복하지 않습니다.
- 해제 성공 시 목록에서 즉시 제거하고 첫 페이지를 재조회해 빈자리/마지막 항목/삭제·숨김 변화를 반영합니다. 후속 조회 실패는 해제 실패와 구분하고 완료 상태를 유지합니다. 더 보기 실패는 기존 항목·커서를 보존합니다.
- 요청 세대 및 AbortSignal로 이전 목록·처리 응답을 폐기합니다. 창 복귀 시 목록을 갱신하며 구독/요청은 정리합니다. React StrictMode의 개발 재마운트에서 자식 목록 요청 전 controller를 활성화합니다.
- 최신 dev에 로그인·토큰 저장 기능은 있지만 스크랩에 공통 인증 session을 공급하는 연결은 없어 실제 보호 API는 fetch 전에 AUTH_PENDING으로 차단합니다. 별도 로그인/토큰 저장/인증 우회는 추가하지 않았습니다. `ScrapProvider`의 선택적 `{userId,token}` session 입력은 기존 내 지원 내역과 같은 명시적 전달 방식입니다. 인증 연결 시 이 입력에 공통 session을 공급하면 계정/토큰 변경마다 이전 화면·controller가 폐기됩니다. 목록·상세 조회에도 같은 token을 전달합니다.
- #37은 최신 dev에 병합됐습니다. 후속 작업으로 `src/pages/MyProfilePage.tsx`의 `내 활동 바로가기`에 `/me/scraps` 링크를 추가해야 합니다. 미병합 파일은 가져오지 않았고 알림·설정은 수정하지 않았습니다.

## 직접 확인

기존 서버가 이 저장소 `frontend`의 5174에서 실행 중임을 확인했습니다. 예시 모드가 꺼져 있어 해당 Vite만 종료하고 **같은 포트에서 재시작**했습니다. 환경 파일 없이 프로세스에 `VITE_POSTS_MOCK=true`를 지정했으며 `--strictPort`를 사용했습니다. 여러 개발 서버를 유지하지 않습니다.

- 스크랩 목록: http://127.0.0.1:5174/me/scraps?scrapExample=true
- 모집글 목록: http://127.0.0.1:5174/posts?scrapExample=true
- 상세: http://127.0.0.1:5174/posts/1?scrapExample=true&viewer=other
- 마지막 1개: http://127.0.0.1:5174/me/scraps?scrapExample=true&scrapCase=last
- 실제 API 인증 대기: http://127.0.0.1:5174/me/scraps

예시 전제는 개발 서버 + `VITE_POSTS_MOCK=true` + **`scrapExample=true`**입니다. production이나 쿼리 없는 스크랩 화면은 예시를 사용하지 않습니다. 예시 상태는 탭 모듈 메모리에만 있으며 화면 간 이동 때 유지되고 새로고침/예시 초기화로 초기화됩니다. 실제 사용자·토큰·DB 저장과 무관합니다.

1. 북마크 클릭은 상세로 이동하지 않고 파란 저장 상태를 바꿉니다. 제목/카드 클릭은 상세로 이동합니다. 목록→상세→내 스크랩 이동 후 같은 상태를 확인합니다.
2. 내 목록의 더 보기, 마감 카드, 해제 후 제거를 확인합니다. `scrapCase=last`에서 마지막 항목을 해제하면 빈 화면이 됩니다.
3. 상단 목록 선택의 empty/error/moreError/slow/restricted, 처리 선택의 failure/expired/unverified/own/missing/network/refreshFailure를 확인합니다. network에서는 상태만 재조회합니다. 실패 시 가짜 성공/데이터 전환이 없어야 합니다.
4. 사용자 alice/bob/guest/owner를 전환합니다. 이전 목록이 남지 않아야 하며 owner는 본인 글 등록 불가, guest는 로그인 안내를 표시합니다.
5. PC/모바일에서 카드와 북마크, 오류 메시지 줄바꿈, 키보드 Tab/Enter/Space 동작을 확인합니다.

## 검증과 한계

- build·lint 통과. `git diff --check` 및 충돌 표시 검사 통과.
- 새 `node scripts/check-scraps.mjs` 24개 통과: API/권한/무토큰 차단, 등록·해제 성공/실패, 중복 처리, 본인 글, 빈 목록/더 보기/마지막 해제, 후속 조회 실패, 늦은 응답·사용자 변경, 첫 조회 중 처리 성공, 서버 재등록 상태 반영, 응답 유실/상태 재조회, 카드 링크 분리, 기존 경로, 명시적 예시/계정 격리.
- 기존 9개 검증 스크립트 131개 통과: 목록/상세 12, 내 지원 22, 내 모집글 14, 수정 11, 지원자 관리 17, 마감/연락처 17, 작성 14, 지원 10, 검색/필터 14.
- 자동 검증은 fetch 가로채기·상태 제어·정적 React 렌더입니다. 실제 JWT·백엔드·DB·CORS 통합 검증은 실행하지 않았습니다.
- Windows 브라우저 접근을 시도했지만 사용자 입력 감지가 반복되어 입력을 중단했습니다. 에이전트의 PC/모바일 시각·실제 클릭 검증은 미완료입니다. HTTP 200을 화면 검증으로 간주하지 않습니다.
- 사용자가 개발 예시 모드에서 화면과 동작을 직접 확인했고 문제없음을 확인했습니다. 에이전트의 브라우저 검증과 구분합니다.

## 최종 반영 상태 (2026-11-16)

- git fetch origin 후 최신 dev 2320508을 fast-forward로 반영하고 미커밋 스크랩 작업을 복원했습니다. 충돌은 없었으며 인증·프로필, 로고 및 실제 페이지 검사 개선을 유지했습니다. 미병합 PR을 가져오지 않았습니다.
- 기존 stash 2개와 이번 dev 반영 전 안전 보관 stash를 모두 유지했습니다.
- 후속 작업: 공통 인증 session의 userId/token 공급, 마이페이지 내 활동 바로가기 연결, 실제 인증·백엔드·DB·CORS 통합 검증 및 모바일 디자인 대조.
