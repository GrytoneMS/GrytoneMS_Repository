# 회원 탈퇴 (AUTH-09)

## PR #52 최신 dev 통합 (2026-11-17)

- #50·#51·#53이 병합된 `origin/dev=60111ae`를 AUTH-09에 병합. App.tsx의 양쪽 import·라우트를 모두 보존해 `/forgot-password`, `/reset-password`(confirm), `/settings/withdraw`를 함께 등록. 인증·스크랩·알림 Provider 및 설정·관리자 등 기존 라우트 유지.
- 설정 홈의 준비 중 항목을 비밀번호 재설정 `/forgot-password`, 회원 탈퇴 `/settings/withdraw` 링크로 연결.
- 탈퇴·비밀번호 재설정 화면은 최종 공통 BrandLogo 사용. #53 SiteHeader·로고 원본·메뉴 정렬과 기능 로직은 변경하지 않음.
- `frontend`에서 `npm.cmd run build`, `npm.cmd run lint`, `node scripts/check-withdrawal.mjs`(10), `node scripts/check-notification-settings.mjs`(8), `node scripts/check-password-reset.mjs`(8), `node scripts/check-posts.mjs`(12), `node scripts/check-auth-session.mjs`(16), `node scripts/check-safety.mjs`(12), `node scripts/check-admin.mjs`(17) 통과. 총 83개 검사. 로고 전용 스크립트는 없으며 기존 정적 렌더 검사에 두 화면의 공통 로고 및 설정 링크 검증을 보강.
- 충돌 표시 검사·`git diff --check` 수행. 자동 검사는 요청 가로채기·상태·정적 렌더 범위이며 이번 통합에서 실제 브라우저 확인·메일 발송·실제 탈퇴·서버 후처리 검증은 수행하지 않음. 아래 과거 사용자 확인과 구분.
- 실행 중 Vite 5173은 현재 저장소의 AUTH-09를 사용. 5174는 기존 점검 worktree `audit/auth-profile-notifications-20261117`로 유지. 기존 stash 3개와 점검 worktree 보존. 서버 재시작 없음.
- 수동 확인: `http://localhost:5173/settings`, `/settings/withdraw`, `/forgot-password`, `/reset-password`(토큰 없으면 안내). 기존 계정에 실제 탈퇴 요청을 보내지 말 것.

아래는 최초 구현 당시 기록이며, 미병합 PR·설정 후속 연결·직접 PNG 사용에 대한 항목은 위 통합 기록으로 대체됨.

## 기준과 연결

- README·frontend README·Git 상태 확인. 깨끗한 작업 트리에서 fetch한 최신 `origin/dev=4928c06`으로 `feature/front-AUTH-09` 생성. 기존 AUTH-05·NOTI-03 브랜치와 stash 3개 보존.
- #50·#51 모두 OPEN/미병합. 미병합 코드를 가져오지 않았으며 설정 화면·비밀번호 재설정 화면을 복제하지 않음.
- #50 병합 후 `SettingsPage.tsx` 계정 관리의 회원 탈퇴 준비 항목을 `/settings/withdraw` Link로 교체. 이번 공통 변경은 App의 import·공개 라우트 등록뿐.

## 서버 계약과 정책

- UserController, WithdrawRequest, WithdrawalCheckResponse, AccountService, User.withdraw, PostRepository, PostDeadlineService, ApplicationRepository, SchoolVerification, ErrorCode 및 공통 인증 코드를 대조.
- GET `/api/users/me/withdrawal-check`: `{ openPostCount, acceptedTeamCount }`. 음수가 아닌 정수 검증, 실패/손상 응답을 0건으로 대체하지 않음. 두 값은 경고용이며 1건 이상이어도 탈퇴 가능.
- openPostCount는 숨김·삭제가 아닌 모집 중인 내 글 수. acceptedTeamCount는 내 수락된 지원 수이며 팀장 소유 팀 전체 수로 해석하지 않음.
- DELETE `/api/users/me`: `{ password }`, 성공은 공통 success 응답. 비밀번호는 NotBlank이며 새 비밀번호 규칙을 적용하지 않음. 공백을 제거해서 보내지 않음. PASSWORD_MISMATCH(400)는 입력 재시도, 403은 권한 안내, 401은 공통 인증 만료 처리.
- 서버는 숨김·삭제가 아닌 모집 중인 내 글 최신 최대 100개를 마감하고 역할·대기 지원도 마감, 대기 지원자에게 POST_CLOSED 알림. 내가 보낸 PENDING 지원은 CANCELED 처리. ACCEPTED 참여 기록을 취소하지 않음.
- 역할 태그·기술·프로필 항목 삭제, 대기 DOCUMENT 인증을 반려로 닫고 문서 이미지 삭제. 프로필 이미지 삭제. User의 이메일·학교 이메일·학과·학년·소개·프로필 이미지 URL을 비우고 닉네임 변경, WITHDRAWN 및 deletedAt 설정.
- 모든 개인정보·모든 활동 기록·계정 행이 삭제된다고 안내하지 않음. 학교·비밀번호 해시 등 User.withdraw에서 비우지 않는 필드가 있고 모집글·지원 기록도 남을 수 있음. backend 수정 없음.
- JwtAuthenticationFilter에서 WITHDRAWN 사용자는 인증되지 않으므로 이후 보호 API는 401. 성공 응답 확인 후 기존 clearAccessToken와 store.sync로 양쪽 토큰 저장소·공통 세션을 정리. 공통 epoch/scope 변경으로 개인 화면·스크랩·알림 상태 격리. 다른 계정의 늦은 성공 응답으로 새 계정을 로그아웃시키지 않음.

## 화면과 불확실한 결과

- 기존 인증 화면의 auth-card·Input·Button 재사용. 비로그인·인증 확인 중·만료 시 비밀번호 폼 숨김. 학교 인증을 임의로 필수화하지 않음.
- 사전 조회 후 비밀번호 입력 → 최종 확인 펼치기 → 명시적 동의 체크 → 탈퇴 제출. 취소는 비밀번호/동의를 초기화하고 요청하지 않음. 비밀번호 스토리지·로그 기록 없음.
- 처리 중 입력·중복 요청 차단. 성공 시 입력 제거, 공통 로그아웃 후 완료 안내·홈·로그인 링크 유지.
- 네트워크 오류·5xx·손상 응답은 탈퇴 여부 불명확 상태. 자동/즉시 재전송하지 않으며 별도 로그인 상태 GET과 로그인 링크 제공. 계정 GET 성공도 앞선 요청의 최종 결과를 보장하지 않음. 401/로그인 불가 역시 만료 등 원인이 있으므로 탈퇴 완료로 단정하지 않음.
- 계정 전환·이탈·공통 인증 재확인 시 기존 폼/비밀번호 제거 및 요청 취소·늦은 응답 폐기. 클라이언트 취소가 서버 탈퇴 취소를 보장하지 않음.

## 실행과 수동 체크리스트

사용자가 탈퇴 동작을 직접 확인했고 잘 된다고 확인함. 아래 개별 항목과 서버의 데이터 정리·모집글 마감·지원 취소·파일 삭제까지 검증 완료로 확대하지 않음.

`cd frontend` → `npm.cmd run dev`. 터미널 주소 사용(기본 `http://localhost:5173/settings/withdraw`). #50 병합 전에는 직접 주소로 진입.

1. 비로그인·확인 중·만료 시 개인 건수와 비밀번호 입력이 없는지 확인. 로그인 후 실제 사전 조회 응답과 건수 비교.
2. 최초 조회를 실패시켜 0건으로 표시하지 않고 다시 조회를 제공하는지 확인. 성공 전 탈퇴 폼 사용 불가 확인.
3. 비밀번호 누락·동의 누락·최종 확인 취소 시 DELETE 요청이 없는지 확인. 기존 계정에는 DELETE를 보내지 말 것.
4. 승인된 소모성 계정이 별도로 준비된 경우에만 잘못된 비밀번호·중복 클릭·실제 탈퇴 성공 확인. 성공 후 입력·세션·개인 화면 제거 및 홈/로그인 동선 확인.
5. 응답 유실을 재현할 때는 소모성 계정만 사용. 불확실 안내와 상태 조회 GET만 발생하고 DELETE 재전송이 없는지 확인. 결과 불명확 상태에서 재탈퇴하지 말고 계정 상태 확인.
6. PC/모바일에서 가로 넘침, Tab/Shift+Tab 순서·포커스, Space 동의 선택, Enter 제출 및 취소 동작 확인.

## 검증 결과

- build·lint 통과. 탈퇴 검사 10개, 공통 인증 16개, 모집글 12개: 총 38개 자동 검사 통과. `git diff --check` 통과.
- 자동 범위: 요청 가로채기·Controller·실제 공통 세션/토큰 정리 함수·정적 렌더. 사전 조회 실패·손상 데이터·잘못된 비밀번호·확인 취소·중복 제출·응답 유실·계정 전환·성공 후 세션 제거 검사. 실제 브라우저 클릭 검증과 구분.
- 실제 연결: 후속 요청에 따라 기존 로컬 DB·Redis·백엔드와 Vite를 실행. 공개 API 200·무토큰 사전 조회 401 확인. 인증된 실제 건수 조회는 도구로 확인하지 못함. 프론트 API 주소는 Git 제외된 로컬 환경 파일에 적용.
- 실제 탈퇴: 사용자가 직접 동작 확인 완료. 에이전트는 실제 DELETE를 보내지 않았고 DB 직접 수정 없음. 어떤 계정을 사용했는지와 개별 서버 후처리 결과는 별도 확인되지 않았음.
- 브라우저·사용자 수동 확인: 탈퇴 동작은 사용자 확인 완료. 개별 PC/모바일·키보드·오류 분기 및 서버 후처리는 미검증으로 유지.
- 로고 수정: auth-page의 텍스트 로고를 기존 `/brand/team-on.png`로 교체. 모집 헤더의 `post-logo` 스타일을 재사용해 148px·760px 이하 124px 및 원본 비율 유지. alt와 홈 링크도 기존 헤더와 동일. 공유 레이아웃·공유 CSS·이미지 파일은 수정하지 않음.
- 최종 정리: fetch한 최신 origin/dev `4928c06`과 기준 커밋 동일. #50·#51 미병합으로 추가 병합·충돌 없음. 설정 메뉴 연결은 #50 병합 후 후속 항목.
- 전체 로고 통일과 공통 BrandLogo는 이 변경에 포함하지 않음. 탈퇴 페이지는 기존 공통 PNG를 직접 참조하며 독립적으로 동작.
- 탈퇴 관련 파일만 커밋·일반 푸시하고 dev 대상 PR로 검토. 원격 병합·backend 코드·기존 stash 변경 없음.
