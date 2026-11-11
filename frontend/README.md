# 프론트엔드 기본 구조

React + Vite + TypeScript 기반입니다. 모집글 목록·상세는 1차 명세의 조회 API와 개발용 예시 모드를 지원합니다.
실행·확인 주소와 연동 범위는 [모집글 작업 안내](./POSTS.md)를 참고하세요.
모집글 작성(S07 / POST-01)은 [작성 화면 작업 안내](./POST-CREATE.md)를 참고하세요.
나머지는 경로 확인용 화면이며 로그인·프론트 권한 검사·지원 동작은 구현하지 않았습니다.

## 설치와 실행

팀 공통 Node.js 버전은 **24.x**입니다(`.nvmrc`, package.json engines 참고).
npm과 package-lock.json을 사용합니다.

```bash
cd frontend
npm ci
cp .env.example .env
npm run dev
```

PowerShell에서는 `Copy-Item .env.example .env`를 사용합니다.
실행 정책으로 npm.ps1이 차단되면 `npm.cmd ci`, `npm.cmd run dev`처럼 실행합니다.
터미널에 출력된 주소(기본 http://localhost:5173)로 접속하세요.
모집글 예시 화면은 VITE_POSTS_MOCK=true인 개발 서버에서 백엔드 없이 확인할 수 있습니다.
나머지 자리 표시 화면도 백엔드 없이 확인할 수 있습니다.
Windows에서 의존성을 다시 설치할 때는 실행 중인 개발 서버를 `Ctrl+C`로 먼저 종료하세요.

```bash
npm run build    # TypeScript 검사 + dist 생성
npm run lint     # Oxlint: 공식 Vite 템플릿 기본 설정
npm run preview  # 빌드 결과 로컬 확인
```

## 환경 변수와 API

.env.example의 `VITE_API_BASE_URL=http://localhost:8080`을 .env에 복사하고
API 서버 주소에 맞게 변경합니다. 변경 후 개발 서버를 다시 실행하세요.
.env는 커밋하지 않습니다. VITE_ 변수는 브라우저에 공개되므로 비밀값을 넣지 않습니다.

`src/api/client.ts`의 `apiRequest<T>(path, options)`는 JSON 요청/응답을 처리합니다.
path는 /로 시작하는 상대 API 경로입니다. body는 JSON으로 직렬화합니다.
method, headers, signal 등 fetch 옵션과 요청별 token을 전달할 수 있습니다.
token을 전달하면 Authorization: Bearer 헤더를 붙이며 자동으로 저장하거나 읽지 않습니다.

- `{ success: true, data: ... }`: data 반환
- `{ success: false, code, message, fieldErrors }`: ApiError 발생
- ApiError: HTTP status, code, message, fieldErrors 보존
- 잘못된 JSON/응답 규약: INVALID_RESPONSE
- 성공 규약과 HTTP 실패가 충돌: HTTP_ERROR
- 네트워크 오류와 취소: fetch 오류를 그대로 전달

fieldErrors는 1차 명세의 { field, message }[]와 기존 Record<string, string> 형식을 지원합니다.
T는 컴파일 시 계약이며 data의 런타임 검증은 기능별 API에서
필요에 따라 추가합니다. 목록·상세는 VITE_POSTS_MOCK=false일 때 실제 API 요청을 보냅니다.

## 폴더별 역할

```text
src/
  pages/       화면별 독립 컴포넌트
  components/  Button, Input, 자리 표시 공통 컴포넌트
  layouts/     공통 화면 틀과 개발용 링크
  api/         fetch 공통 처리 (기능별 API는 추후 추가)
  styles/      tokens.css: 폰트·색·여백 / global.css: 기본 반응형 스타일
  types/       공통 API 응답 타입
  App.tsx      페이지 라우트 등록
  main.tsx     BrowserRouter와 전역 스타일 진입점
```

기본 색상은 #1F6FEB입니다. CSS 변수와 기본 CSS만 사용합니다.
디자인은 추후 피그마 확인 후 적용합니다. Button은 기본 type이 button이며,
폼 전송 시 type="submit"을 지정합니다. Input은 필수 label, 선택 error와 input 속성을 받습니다.

## 페이지 경로

| 경로 | 페이지 |
| --- | --- |
| / | /posts로 이동 |
| /login | 로그인 |
| /signup | 회원가입 |
| /school-verification | 학교 인증 |
| /profile/setup | 초기 프로필 설정 |
| /posts | 모집글 목록 |
| /posts/:postId | 모집글 상세 |
| /posts/new | 모집글 작성 |
| /posts/:postId/applicants | 모집글 지원자 관리 |
| /me/profile | 내 프로필 |
| /me/profile/edit | 프로필 수정 |
| /users/:userId | 다른 사용자 프로필 |
| /me/posts | 내 모집글 |
| /me/applications | 내 지원 내역 |
| 나머지 | 페이지 없음 안내 |

동적 경로 링크의 example-post와 example-user는 자리 표시 ID입니다.
지원하기는 추후 모집글 상세에서 여는 모달로 구현하며 독립 경로는 없습니다.
현재 모든 경로는 인증 없이 열립니다. 배포 시 직접 URL 접근과 새로고침이 동작하도록
프론트 경로를 index.html로 보내는 SPA fallback 설정이 필요합니다.

## 새 페이지 추가

1. src/pages에 화면 이름의 .tsx 컴포넌트를 만듭니다.
2. src/App.tsx에서 import하고 공통 레이아웃 아래 Route를 등록합니다.
3. 필요하면 src/layouts/AppLayout.tsx에 개발용 확인 링크를 추가합니다.
4. 페이지 전용 CSS는 화면 근처에 두고 공통 값은 기존 CSS 변수를 사용합니다.
5. 직접 URL 접근, 링크 이동, 좁은 화면을 확인하고 빌드·린트를 실행합니다.

## 두 명이 협업하는 방법

이번 feature/front-setup이 dev에 합쳐진 후 각자 최신 origin/dev에서 담당 기능 브랜치를
만듭니다. 루트 README의 기능 ID 규칙을 따릅니다. 작업 중인 변경은 먼저 안전하게 보존합니다.

```bash
 git fetch origin
 git switch -c feature/front-LIST-01 origin/dev
```

각자 맡은 페이지 파일 중심으로 개발하고 빌드·린트를 확인한 뒤 기능 브랜치를 푸시하여
**base가 dev인 PR**을 만듭니다. .github/pull_request_template.md에 작업 내용, 기능 ID,
검증 결과와 화면 캡처를 기록합니다. main에는 직접 푸시하지 않습니다.
커밋 메시지는 루트 README의 `타입: 내용 (기능ID)` 형식을 따릅니다.

App.tsx, 공통 레이아웃, Button/Input, API 클라이언트, 공통 타입, CSS 변수,
package.json과 lockfile 등 **공통 파일은 수정 전에 상대 담당자에게 공유**하고 범위를 조율합니다.
공통 변경을 먼저 합친 뒤 다른 쪽이 반영하면 충돌을 줄일 수 있습니다.
의존성 변경 시 lockfile도 함께 관리하고 프론트 작업에서 backend는 수정하지 않습니다.

## 남은 결정 사항

- 로그인 유지와 토큰 보관 위치(메모리·쿠키·스토리지), 만료·갱신·로그아웃 처리
- 인증 필요 경로와 학교 인증/초기 프로필 완료에 따른 접근 제어
- 실제 API 엔드포인트, 데이터 타입, fieldErrors 구조, CORS·쿠키 사용 여부
- 피그마 기반 디자인, 폰트와 공통 컴포넌트 상세 상태
- 담당 화면 분배, 지원 모달, 배포 서버의 SPA fallback 설정
