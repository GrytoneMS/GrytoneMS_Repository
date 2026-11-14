# 모집 마감·연락 수단 조회 · POST-04 / APPL-07

## 기준과 범위

2026-11-14 `git fetch origin` 후 `origin/dev`의 `bc73e39`에서 `feature/front-POST-04`를 생성했습니다. 시작 작업 트리는 깨끗했습니다. PR #33은 MERGED(2026-11-13), #34는 OPEN이며 기존 `feature/front-APPL-04`를 보존했습니다. #34의 미병합 지원자 관리·목록 문구를 가져오지 않았습니다. dev의 상세·지원 모달 위에 기능을 연결했으며 지원자 관리 화면은 수정하거나 연결하지 않았습니다.

독립 API·기능 명세 원문과 `docs` 폴더는 저장소에 없습니다. 기능 ID와 계약은 기존 프론트 문서, 최신 서버의 `PostController`, `PostService.closePost`, `ApplicationController`, `ApplicationService.getContact`, `ContactResponse`, `ApiResponse`, `SecurityConfig`, `ErrorCode`, 관련 서버 테스트를 대조했습니다. 독립 명세 원문 대조는 미완료입니다.

Figma 파일 `TQAHXMAyHdCqU5aV3qBmhW`를 읽기 전용으로 조회해 상세 S05 (`8:99`), 지원자 관리 S08 (`8:419`) 및 연락 수단 영역의 메타데이터를 확인했습니다. 이어서 두 화면의 디자인 컨텍스트·스크린샷을 요청했으나 Starter 플랜 MCP 호출 한도에 걸렸습니다. 따라서 디자인 시각 일치 검증은 미완료입니다. 기존 상세·Button·panel·스타일·lock.svg를 재사용했으며 화면을 재구성하지 않았습니다.

backend·다른 담당 화면·공통 인증/API/컴포넌트·package/lockfile·환경 파일은 수정하지 않았습니다. DB 삽입/변경을 하지 않았습니다. 사용자 화면 확인 후 커밋·일반 푸시·dev 대상 PR 생성만 진행하며, PR 병합과 다음 기능 개발은 포함하지 않습니다.

## 서버 계약과 동작

- `PATCH /api/posts/{postId}/close`, 본문 없음 → HTTP 200 `{ success: true, data: null }`.
- 로그인한 팀장만 가능. 숨김·삭제·없는 글은 NOT_FOUND, 다른 사용자는 FORBIDDEN, 이미 마감은 POST_ALREADY_CLOSED(409).
- 글과 모든 역할을 CLOSED로 바꾸고 PENDING 지원만 CLOSED로 바꿉니다. 대기자에게 POST_CLOSED 알림을 보냅니다. ACCEPTED·REJECTED·CANCELED 등 다른 상태와 선발 인원은 유지합니다.
- 확인창에서 영향 설명 후 확정합니다. 동일 틱의 중복 제출도 차단합니다. 성공 즉시 상세·역할을 마감으로 반영한 다음 상세를 별도로 재조회합니다. 재조회 실패는 마감 실패가 아니며 확정 CLOSED 상태를 보존합니다. 지연된 OPEN 조회 응답으로 재개하지 않습니다.
- 통신 실패·불명확한 성공 응답 등은 성공으로 표시하지 않고 최신 상세 조회 전 재마감을 잠급니다. 이미 마감 오류는 서버 확인 상태로 반영합니다. 재모집 기능은 없습니다.
- `GET /api/posts/{postId}/contact` → HTTP 200 `{ success: true, data: { contactLink: "https://…" } }`.
- 로그인한 팀장 또는 해당 글의 ACCEPTED 지원자만 가능. 일반·대기·거절·취소·마감 지원·LEFT·비회원은 조회하지 않습니다. 글 상태 CLOSED 자체는 열람을 막지 않습니다. 서버는 삭제된 글을 NOT_FOUND로 처리하며, 연락 수단 조회에는 마감일/모집 상태 조건을 추가로 검사하지 않습니다.
- 연락 수단은 별도 API 성공 후에만 메모리 상태에 들어가고 화면에 표시됩니다. 일반 상세/목록·숨김 HTML에 연락처를 넣지 않습니다. HTTP(S) 절대 URL만 허용하고 자격증명·공백/제어문자·상대 URL·javascript/data/기타 프로토콜은 거부합니다. 링크는 새 창·noopener/noreferrer/no-referrer로 열며 복사 실패는 화면에 안내합니다.
- 재조회·숨기기·권한 상실 시 기존 연락 수단과 복사 안내를 제거합니다. 게시글·사용자·토큰·지원 권한이 바뀌면 컴포넌트를 교체하므로 이전 연락처는 렌더에서 즉시 사라집니다. cleanup에서 요청을 abort하고 세대/생존 검사로 늦은 응답과 복사 완료를 폐기합니다. localStorage·로그에 저장하지 않습니다.

## 인증 연동 대기

현재 dev에는 공통 인증 컨텍스트가 없고 로그인 화면은 자리 표시입니다. 별도 인증·토큰 저장을 만들지 않았습니다. 실제 UI는 토큰을 공급하지 않으므로 마감/연락 수단 API 요청을 보내지 않으며 인증 연동 대기를 안내합니다. API 함수도 토큰 누락·빈 문자열·공백이면 fetch 전에 차단합니다. 실제 API 실패를 mock 성공으로 바꾸지 않습니다.

향후 인증 담당자는 상세 조회의 토큰과 `PostActions.session`의 `{ userId, token }`을 **같은 공통 인증 세션**에서 공급하고, 로그아웃 시 session을 제거해야 합니다. 이 prop은 주입 지점이며 인증 저장소가 아닙니다. 사용자 id 변경·토큰 변경·로그아웃 시 key가 바뀌어 기존 연락 수단과 요청 상태를 제거합니다. 공통 인증이 없는 현재 실제 사용자 전환/로그아웃 UI는 없으며 자동 검증에서 이 경계를 확인했습니다.

## 개발 예시

기존 `import.meta.env.DEV && VITE_POSTS_MOCK === 'true'`에서만 활성화합니다. 실제 모드에서는 viewer/closing/contact 쿼리로 권한·API 결과를 바꿀 수 없습니다. 예시는 현재 화면의 메모리에서만 동작하며 새로고침/화면 전환 시 초기화됩니다. 예시 연락 수단은 별도 예시 조회 성공 시 생성하는 `https://example.com/team-contact`입니다. 일반 mock 상세에는 추가하지 않았습니다.

```powershell
cd frontend
$env:VITE_POSTS_MOCK='true'
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

이번 작업에서는 5173 리스너가 없음을 확인하고 위 명령으로 예시 서버 하나를 실행했습니다. `.env`는 수정하지 않았습니다. 이미 서버가 있으면 중복 실행하지 말고 재사용하세요. 종료는 실행 터미널에서 Ctrl+C입니다. 실제 API 모드는 서버를 종료한 다음 `VITE_POSTS_MOCK=false`로 재실행합니다.

| 주소 (http://127.0.0.1:5173 뒤에 붙임) | 확인할 사례 |
| --- | --- |
| `/posts/1?viewer=owner` | 팀장 마감 확인창·취소·확정·모집 현황 갱신, 연락 수단 조회 |
| `/posts/1?viewer=owner&closing=failure` | 마감 서버 실패, 성공 안내 없음 |
| `/posts/1?viewer=owner&closing=refreshFailure` | 마감 성공 유지, 최신 상세 재조회만 재시도 |
| `/posts/1?viewer=owner&closing=alreadyClosed` | 확정 직전 이미 마감된 상태 |
| `/posts/1?viewer=owner&closing=forbidden` | 처리 시점 권한 변경 |
| `/posts/1?viewer=accepted` | 수락자 연락 수단 조회·열기·복사·숨기기 |
| `/posts/1?viewer=accepted&contact=forbidden` | 조회 시점 권한 변경, 연락 수단 없음 |
| `/posts/1?viewer=accepted&contact=failure` | 조회 실패·재시도 |
| `/posts/1?viewer=guest` | 비회원 조회 불가 |
| `/posts/1?viewer=applied` | 대기자 조회 불가 (rejected/canceled/applicationClosed/left도 동일) |
| `/posts/12?viewer=owner` | 이미 마감된 본인 글, 재마감 불가·연락 수단 가능 |
| `/posts/12?viewer=accepted` | 마감 후 수락자 연락 수단 조회 가능 |
| `/posts/1?viewer=other` | 기존 지원 모달 동작, 연락 수단 조회 불가 |

상세의 개발 패널에서 마감·연락 수단 결과를 바꿀 수 있습니다. expired/missing으로 인증 만료/삭제 예시도 확인합니다.

수동 체크리스트:

- 본인 글에서 확인창 취소·ESC·Tab 순환·초기 취소 포커스·닫은 뒤 포커스 복귀·스크롤 잠금 확인.
- 확정 연속 클릭/Enter 시 1회만 처리, 글·모든 역할 마감 표시와 지원 비활성화 확인.
- 재조회 실패 예시에서 마감 완료 유지·재마감 금지·조회만 재시도 확인.
- 수락자로 연락 수단을 조회한 뒤 예시 사용자를 대기·비회원으로 변경: 기존 주소가 즉시 사라지고 늦은 응답도 표시되지 않는지 확인.
- 조회 중 게시글 이동, 표시 후 숨기기, 조회 실패·재시도, 주소 새 창 열기·복사 및 복사 거부 안내 확인.
- 375px/1440px에서 확인창·주소 줄바꿈·버튼 접근 확인.

에이전트 브라우저 연결은 `The browser access host is unavailable`로 실패했고, Chrome 앱 진단도 URL 확인 제한으로 중단되었습니다. 에이전트가 브라우저 클릭·렌더·포커스·반응형을 직접 확인한 것은 아닙니다.

사용자가 Console에서 의존성 요청의 `504 (Outdated Optimize Dep)`를 확인했습니다. 기존 프로젝트 Vite 서버만 종료하고 mock 환경을 유지해 `npm.cmd run dev -- --force --host 127.0.0.1 --port 5173 --strictPort`로 재실행했습니다. 의존성 재최적화와 React/React DOM/React Router/JSX runtime 요청 복구, DEV 및 VITE_POSTS_MOCK=true를 확인했습니다. 소스·lockfile 수정이나 node_modules 재설치는 하지 않았습니다.

그 후 사용자가 화면이 정상적으로 열린다고 확인했습니다. 역할 카드에서 막대와 버튼이 붙는 문제는 `.post-role-panel > progress + .button`에 `display: block; margin-top: 16px`를 적용해 수정했습니다. 기존 아래쪽 카드 여백 20px와 자동 높이, 버튼 스타일·동작을 유지하며 다른 화면에는 적용되지 않습니다. 사용자가 수정 화면도 직접 확인했고 정상이라고 알려 **개발 예시 사용자 화면 확인 완료**로 기록합니다. 항목별·뷰포트별 상세 확인 범위는 별도 보고되지 않았으므로 위 수동 체크리스트 전체를 개별 검증했다고 간주하지 않습니다.

**인증 연결과 실제 백엔드 마감/연락 수단 조회 성공·JWT·CORS 검증은 남은 작업입니다.** 개발 예시 화면 확인을 실제 서버 성공으로 기록하지 않습니다.

## 자동 검증 결과

frontend 기준 아래 명령 통과. API는 fetch 가로채기, 상태/표시는 컨트롤러·정적 렌더와 명시적 mock 검증입니다. 실제 서버/DB/브라우저 검증으로 기록하지 않습니다.

| 명령 | 결과 |
| --- | --- |
| `npm.cmd run build` | 통과 |
| `npm.cmd run lint` | 통과, 경고 없음 |
| `node scripts/check-post-actions.mjs` | 17개 통과 |
| `node scripts/check-post-actions.mjs --mock` | 17개 통과 |
| `node scripts/check-applications.mjs` | 10개 통과 |
| `node scripts/check-applications.mjs --mock` | 11개 통과 |
| `node scripts/check-posts.mjs` | 11개 통과 |
| `node scripts/check-post-filters.mjs` | 14개 통과 |
| `node scripts/check-post-create.mjs` | 14개 통과 |

`check-applicants.mjs`는 현재 dev에 없으므로 실행 대상이 아닙니다. 기존 PR #34의 코드를 가져와 실행하지 않았습니다. `git diff --check` 및 충돌 표시 검색도 통과했습니다.
