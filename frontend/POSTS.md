# 모집글 목록·상세 (LIST-01·02 / LIST-05)

## 실행 (Windows Git Bash)

Node.js 24.x, 기존 npm 의존성을 사용합니다. 실행 중인 서버가 있으면 먼저 해당 터미널에서
Ctrl+C로 종료한 뒤 설정을 바꿔 다시 실행하세요. `--strictPort`로 중복 서버의 다른 포트 실행을 막습니다.

```bash
cd frontend
# 예시 데이터. 브라우저에 개발용 표시와 상태 선택 패널이 나타납니다.
VITE_POSTS_MOCK=true npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

실제 API 모드:

```bash
VITE_POSTS_MOCK=false VITE_API_BASE_URL=http://localhost:8080 npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

또는 `.env.example`을 `.env`로 복사해 값을 설정하고 개발 서버를 재시작합니다.
`VITE_POSTS_MOCK`는 정확히 `true`인 개발 서버에서만 적용됩니다. production build/preview는 실제 API 모드입니다.
API 모드에서 HTTP·네트워크 오류가 나도 mock으로 전환하지 않습니다.

## 직접 확인할 주소

| 주소 (http://127.0.0.1:5173 기준) | mock 확인 항목 |
| --- | --- |
| `/posts` | 첫 8개, 더 보기 후 나머지 4개, 마지막 페이지 |
| `/posts?sort=DEADLINE` | 전체 예시 데이터의 마감일 정렬 |
| `/posts?scenario=slow` | 4초 로딩 |
| `/posts?scenario=empty` | 빈 목록 |
| `/posts?scenario=error` | 오류 및 재시도 (정상 패널로 전환해 복구) |
| `/posts?scenario=moreError` | 첫 페이지 유지, 추가 로딩 오류 및 재시도 |
| `/posts/1` | 행사 포함 상세, `/users/1` 프로필 연결 |
| `/posts/2` | 행사 없는 비대면 상세; 없는 위치·빈도 표시 안 함 |
| `/posts/12` | 모집 마감 |
| `/posts/1?viewer=owner` | 본인 모집글의 관리·마감 준비 중 버튼 |
| `/posts/1?viewer=applied` | PENDING 지원 상태 |
| `/posts/1?viewer=accepted` | ACCEPTED 지원 상태 |
| `/posts/1?viewer=rejected` | REJECTED 지원 상태 |
| `/posts/1?viewer=canceled` | CANCELED 지원 상태 |
| `/posts/1?viewer=applicationClosed` | CLOSED 지원 상태 |
| `/posts/1?viewer=left` | LEFT 지원 상태 |
| `/posts/99999` | 없는 모집글 안내 |

실제 API 모드에서는 실제 서버에 존재하는 숫자 ID로 상세 주소를 확인합니다.
`viewer`·`scenario` 쿼리는 실제 API 모드의 데이터·권한을 바꾸지 않습니다.
mock에서도 연락처는 데이터에 포함하지 않으며 연락처 API를 호출하지 않습니다.

체크리스트:

- 목록 카드 → 상세 → 목록 복귀, 브라우저 뒤로가기와 새로고침, 정렬 유지
- 더 보기 중 중복 클릭 방지, 추가 오류 시 기존 카드 유지
- 1440px, 768px, 375px, 320px에서 겹침·잘림·가로 스크롤 확인
- Tab/Enter로 카드·링크·버튼 이동, 포커스 표시, 검색 레이블 확인
- 행사 없는 글은 행사 영역이 없고, 마감·지원 상태는 버튼에 구분되는지 확인
- `/login`, `/signup`, `/school-verification`, `/profile/setup`, `/me/profile`,
  `/me/profile/edit`, `/users/1`의 기존 자리 표시 화면 유지 확인

브라우저 도구 연결은 이전 작업에서 실패하여 재시도하지 않았습니다.
위 시각·상호작용 검증은 사용자의 직접 확인이 필요합니다.

## API 구현

기준: 그려톤_API 명세서 (1차), 3쪽 공통 규칙, 5~7쪽 목록·상세, 11쪽 코드값.

- `src/features/posts/api.ts`: 기존 `apiRequest`를 이용하는 `getPosts`, `getPost`
- 목록: `GET /api/posts?sort=LATEST|DEADLINE&size=20`, 첫 요청 cursor 생략
- 추가 요청: 응답 `nextCursor`를 URLSearchParams로 인코딩해 그대로 전송. 해석하지 않음
- `hasNext=false`면 종료. `hasNext=true`인데 커서가 없거나 직전과 같으면 응답 오류
- 상세: `GET /api/posts/{postId}`, 서버의 `isMine`과 `myApplication.status`로 상태 구분
- `dDay`는 서버 값을 사용. 날짜 문자열은 KST 기준을 그대로 표시
- mock은 동일한 DTO 구조. 목록에서 상세 전용 필드를 제거하고 개발용 페이지만 8개로 분할
- `source.ts`의 데이터 로딩과 페이지 표시 분리. 요청 취소·연속 정렬 변경 시 오래된 응답 무시
- 공통 클라이언트의 `fieldErrors`는 명세의 `{field,message}[]` 및 기존 Record 형식 모두 지원

목록 조회는 비회원 공개 API입니다. 현재 프로젝트에 인증 컨텍스트가 없어 페이지는 토큰 없이 조회합니다.
조회 함수와 데이터 소스는 요청별 `token` 옵션을 받을 수 있도록 했습니다.
인증 담당자가 확정한 컨텍스트에서 이 옵션으로 전달하는 연결이 필요합니다.
localStorage·쿠키·가짜 인증 헤더 또는 별도의 토큰 보관 정책은 추가하지 않았습니다.
`isMine=false, myApplication=null`만으로 비회원/인증 회원을 구분하지 않습니다.
지원·관리 버튼은 상태를 보여주되 동작은 비활성입니다. 로그인·학교 인증 판정과 모달은 구현 범위 밖입니다.

## Figma와 API의 차이

| Figma 정보/동작 | 1차 API 및 처리 |
| --- | --- |
| 목록 총 게시글 수 | 응답에 없음. 표시하지 않음 |
| 카드의 위치 정보 | 목록 응답에 없음. 학교·진행 방식만 표시 |
| 카드 역할의 남은 인원/전체 정원 | 목록에는 remaining만 있음. `남은 N명`만 표시 |
| 상세 역할 충원률 | headcount·filledCount로 표시, 역할 status도 별도 표시 |
| 작성자의 학교 인증 배지·서비스 활동 팀 수 | 상세 leader 응답에 없음. 학교·전공만 표시 |
| 행사 접수 D-day | 행사에는 applyDeadline만 있음. 접수 날짜만 표시 |
| 검색·필터·최근 검색어·모집 중 토글 | 2차 명세 범위. 비활성 안내, 일부 카드만 검색하는 처리 없음 |
| 알림 수·스크랩 상태·신고 | 1차 조회 계약 없음. 임의 수치 없이 준비 중 표시 |
| 연락처 | 별도 APPL-07 권한 API. 목록·상세 DTO/예시 데이터/요청에 포함하지 않음 |

Noto Sans KR은 CSS font-family에 지정되어 있으나 폰트 파일은 저장소에 없습니다.
설치되지 않은 기기에서는 기존 시스템 폰트로 표시됩니다. 공통 폰트 제공 방법은 협의가 필요합니다.

## 검증 명령과 한계

```bash
npm run build
npm run lint
node scripts/check-posts.mjs
```

자동 검증은 요청 URL·정렬·커서·토큰·취소 전달, API 실패 시 mock 미전환,
fieldErrors 호환, mock 응답 필드·정렬·페이지 분할, 전체 지원 상태,
기존 경로의 정적 렌더, 빈 목록·추가 오류·요청 취소를 확인합니다.
Vite middleware 모드와 Node assertion을 사용하며 새 패키지나 테스트용 HTTP 서버는 추가하지 않습니다.
이 검증은 fetch 응답을 가로채므로 실제 서버 통신·인증·CORS·브라우저 레이아웃을 검증하지 않습니다.
현재 로컬 8080 리스너가 확인되지 않았고 실제 백엔드 조회 성공은 확인하지 않았습니다.

협의할 사항: 백엔드 조회 API 준비·실제 응답 확인, 인증 컨텍스트의 token 공급,
학교 인증 상태 연결, 공통 헤더·폰트 제공. 공통 변경은 AppLayout, API 오류 타입·파서에 한정됩니다.
인증·프로필 페이지 파일과 backend는 변경하지 않았습니다.

S06 이후 화면, 지원 제출·지원자 관리·마감 API 호출은 이번 작업에서 구현하지 않습니다.
