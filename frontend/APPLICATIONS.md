# 모집글 지원 모달 S06 · APPL-01

## 구현 범위와 기준

- 최신 `origin/dev`의 `8093aac`에서 `feature/front-APPL-01` 생성. 시작 당시 `feature/front-LIST-03` 작업 트리는 깨끗했음. 검색·필터 PR [#25](https://github.com/GrytoneMS/GrytoneMS_Repository/pull/25)은 병합됨(2026-11-13). 별도 cherry-pick 없음.
- Figma S06 `8:198`, 연결 상세 S05 `8:99`의 디자인 컨텍스트·스크린샷을 읽기 전용 확인. 모달 폭 680px, 여백, 역할 선택, 입력, 공유 프로필 안내, 하단 버튼을 기존 React/CSS/Button/Input 방식으로 구현. 프로필 공개 데이터가 없으므로 예시 수상 이력을 실제 내 정보처럼 표시하지 않음. 기존 상세·헤더의 범위 밖 디자인은 변경하지 않음. 모달 내부에는 새 이미지 자산이 없음.
- 저장소에 별도 API 명세서 원문은 없음. 기존 `POSTS.md`의 1차 API 명세 참조와 최신 서버의 `ApplicationController`, `ApplyRequest`, `ApplyResponse`, `ApplicationService`, `ApplicationStatus`, `PostDetailResponse`, `ErrorCode`, `SecurityConfig`, `BlockPolicy`를 대조해 현재 서버 계약을 적용함. 별도 원문과의 독립 대조는 미완료.
- backend·팀원 담당 화면·공통 API 클라이언트·공통 컴포넌트·패키지/잠금 파일 수정 없음. DB 데이터 삽입 없음. 사용자 화면 확인 후 커밋·푸시·dev 대상 PR 생성 진행. PR 병합과 다음 기능 개발은 범위 밖임.

## 서버 계약

`POST /api/posts/{postId}/applications` → `{ postRoleId, message, availableTime }`.
`postRoleId`는 현재 상세 응답 `roles[].id`이며 역할 코드나 배열 순번을 전송하지 않음.
성공은 HTTP 201, 공통 envelope의 `data: { id, status: "PENDING" }`.

| 입력 | 검증 |
| --- | --- |
| 역할 | 현재 글에 속하고 OPEN이며 잔여 정원이 있는 역할 1개 |
| 지원 메시지 | 필수, 공백만 입력 금지, 20~300자 |
| 가능한 시간 | 필수, 공백만 입력 금지, 최대 100자 |

서버 `@Size`는 trim 이전 UTF-16 문자열 길이를 검사하고 서비스가 저장 직전에 trim함. UI 글자 수와 길이 검사·전송도 이 순서를 따름. 초과 입력을 조용히 자르지 않고 필드별 오류로 안내함.
본인 글, 글/역할 마감, 정원 도달, KST 마감일 경과는 지원 차단.
PENDING·ACCEPTED·REJECTED·CANCELED·CLOSED·LEFT 모두 기존 지원 이력이므로 재지원 불가. 서버의 `existsByPostIdAndApplicantId` 기준과 일치.
인증 만료·학교 미인증·이용 정지·중복·역할 마감·권한·차단 오류를 code로 안내하고 필드 오류 배열/객체 모두 보존함. 상대방의 차단·삭제·숨김은 서버가 NOT_FOUND로 묶어 반환함.

같은 렌더 시점의 연속 제출까지 잠금. 요청 중 필드와 닫기/취소/ESC를 잠그고 결과를 기다림. 실패하면 입력 유지. 성공 응답 즉시 상세의 지원 상태를 기록하고 모달을 닫은 뒤 상세를 별도로 갱신함. 갱신만 실패하면 **지원 완료**를 유지하고 상세 조회만 재시도함. 익명/지연 조회가 `myApplication: null`을 돌려줘도 확인된 완료 상태를 지우지 않음. 이 상태는 현재 상세 컴포넌트 생명주기 동안 유지됨.

## 인증 연동 대기

현재 dev에는 공통 인증 컨텍스트가 없고 `/login`, `/school-verification` 모두 `PagePlaceholder`임. 따라서 자리 표시 화면을 실제 인증 흐름처럼 안내하거나 자동 이동하지 않고 모달에서 연동 대기를 명시함.

- 실제 UI는 토큰을 공급하지 않으며 지원 요청을 보내지 않음. 유효한 입력으로 제출하면 연동 대기 안내와 입력 유지.
- 로그인/토큰 저장·임시 사용자 id·가짜 JWT 구현 없음.
- `applyToPost(postId, draft, { token, signal })`은 공통 `apiRequest` 사용. 공백/누락 토큰이면 fetch 전에 차단.
- 인증 담당 연동 시 공통 컨텍스트의 사용자·토큰으로 상세 조회와 제출 모두 연결해야 함. `ApplicationModal.send`에 공통 토큰을 전달하는 실제 API 함수를 공급할 수 있음. 로그인·인증 구현 후 기존 흐름으로 안내하도록 연결할 것.
- 실제 백엔드 지원 성공, JWT, CORS, 개인별 상세 조회는 **미검증**. 요청 가로채기 자동 검증과 개발 예시 성공을 실제 서버 성공으로 기록하지 않음.

## 직접 확인

기본 주소는 `http://127.0.0.1:5173`. 이미 서버가 있으면 재사용하며 새 서버를 중복 실행하지 않음. 같은 포트가 사용 중이면 아래 명령은 다른 포트로 자동 우회하지 않고 종료함.

이번 작업에서는 5173/5174/8080 포트에 LISTENING 서버가 없는 것을 확인한 뒤 예시 개발 서버 하나를 5173에 실행했으며, `/posts/1?viewer=other`의 HTTP 200 응답을 확인함. 백엔드는 실행하지 않음. HTTP 응답 확인은 브라우저 렌더 검증과 별개임.

```powershell
cd frontend
$env:VITE_POSTS_MOCK='true'
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

조회와 지원 예시는 기존 `import.meta.env.DEV && VITE_POSTS_MOCK === 'true'`에서만 활성화됨. 실제 API 모드는 서버를 종료한 뒤 `$env:VITE_POSTS_MOCK='false'`로 바꾸고 재실행. `.env`의 `VITE_API_BASE_URL`은 실제 백엔드 주소를 사용. 기존 `.env`는 수정하지 않았음. 빌드/preview에서는 예시가 비활성화됨.

| 주소 (위 기본 주소 뒤에 붙임) | 확인할 상태 |
| --- | --- |
| `/posts/1?viewer=other` | 지원 가능. 역할별 버튼은 해당 역할 미리 선택, 일반 버튼은 직접 선택 |
| `/posts/1?viewer=owner` | 본인 글 지원 불가 |
| `/posts/12` | 글 모집 마감 |
| `/posts/1?viewer=other&roles=closed` | 첫 역할 마감, 두 번째 역할만 선택 가능 |
| `/posts/1?viewer=applied` | 대기 상태와 재지원 불가 |
| `/posts/1?viewer=accepted` | 수락 상태 |
| `/posts/1?viewer=rejected` | 거절 상태 |
| `/posts/1?viewer=canceled` | 취소 상태 |
| `/posts/1?viewer=applicationClosed` | 지원 마감 상태 |
| `/posts/1?viewer=left` | 팀에서 나간 상태 |
| `/posts/1?viewer=other&application=success` | 800ms 후 예시 제출 성공 및 완료 상태 |
| `/posts/1?viewer=other&application=failure` | 서버 실패·입력 유지 |
| `/posts/1?viewer=other&application=refreshFailure` | 지원 완료 후 상세 갱신만 실패, 재지원 불가 |
| `/posts/1?viewer=other&application=expired` | 인증 만료 |
| `/posts/1?viewer=other&application=unverified` | 학교 미인증 |
| `/posts/1?viewer=other&application=duplicate` | 중복 지원 |
| `/posts/1?viewer=other&application=closed` | 제출 직전 모집 마감 |
| `/posts/1?viewer=other&application=blocked` | NOT_FOUND 접근 불가 |
| `/posts/1?viewer=other&application=suspended` | 이용 정지 |

개발 예시 패널에서 결과와 첫 역할 마감을 변경할 수도 있음. 예시 제출은 인메모리 상태만 변경하며 실제 API/DB를 호출하지 않음. 새로고침하면 초기 예시 상태로 돌아감. 실제 API 모드에서는 viewer/scenario/roles/application 쿼리가 사용자·응답·요청 결과를 바꾸지 않음.

## 검증 결과와 수동 확인 항목

2026-11-13: `npm.cmd run build`, `npm.cmd run lint` 통과(경고 없음).

```powershell
node scripts/check-applications.mjs          # 실제 API 경로, fetch 가로채기 10개
node scripts/check-applications.mjs --mock   # 명시적 개발 예시 포함 11개
node scripts/check-posts.mjs                # 기존 목록·상세 11개
node scripts/check-post-create.mjs          # 기존 작성 14개
node scripts/check-post-filters.mjs         # 기존 검색·필터 14개
```

입력 필수/공백/19·20·300·301/1·100·101자, 현재 역할 소속/마감/정원, 6가지 지원 상태, KST 마감, 토큰 없는 fetch 0회, 정확한 POST body/역할 id, 동일 틱 중복 방지, 실패 시 원본 유지/재시도, 성공 상태 전달·상세 갱신/실패 분리, HTTP/네트워크/응답 오류와 필드 오류 보존을 자동 검증함. 개발 예시는 실제 fetch 없이 성공/실패함.

사용자가 개발용 예시 모드에서 지원 모달을 브라우저로 직접 확인했고 문제가 없었다고 알려 **사용자 화면 확인 완료**로 기록함. 세부 기기·뷰포트·항목별 확인 범위는 별도 보고되지 않았으므로 아래 모든 항목을 개별 검증했다고 간주하지 않음. 인증 연동 부재로 **실제 백엔드 지원 성공은 여전히 미검증**임.

에이전트 브라우저 도구는 `The browser access host is unavailable`로 연결되지 않았음. 반복 시도하지 않았으며 아래 목록은 재확인용 체크리스트로 유지함:

- 일반/역할별 버튼으로 열기 → 역할 미선택/미리 선택 확인. 마감 역할 선택 불가.
- 빈 값·공백·길이 경계 입력 → 필드 오류와 실시간 글자 수·첫 오류 포커스 확인.
- 닫기·취소·ESC → 모달 닫힘, 열었던 버튼으로 포커스 복귀. 성공 후에는 비활성화된 버튼 대신 완료 안내로 포커스 이동.
- Tab/Shift+Tab이 네이티브 dialog 안에서 순환하고 라디오를 방향키로 선택할 수 있는지 확인. 열린 동안 배경 스크롤 잠김, 닫은 뒤 복구 확인.
- 요청 중 연속 클릭·Enter·닫기·ESC가 중복 전송/미확정 닫힘을 만들지 않는지 확인.
- 실패 뒤 입력 유지, 결과를 성공으로 변경한 뒤 재시도 → 완료 안내·대기 상태 확인.
- 상세 재조회 실패 예시에서 완료 안내와 상세 조회만 재시도 버튼 확인.
- 320/375/768/1440px 및 모바일 키보드가 열린 상태에서 긴 입력·폼 스크롤과 하단 제출 버튼 접근 확인.
- 실제 모드에서 유효 입력 제출 → 인증 연동 대기 안내, Network에 지원 POST 없음 확인.
- 목록→상세→목록 복귀, 기존 검색/필터와 작성 화면 동작 확인.

지원자 관리·수락·거절·취소·연락처 공개는 다음 작업 범위임.
