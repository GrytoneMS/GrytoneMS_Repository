# TEAM:ON 로고 및 공통 헤더 통일

## 최종 원본 확인

- 사용자 지정 원본: `C:\Users\user\Downloads\team_on_3 1.png`.
- 원본과 `public/brand/team-on.png`를 모두 이미지로 열어 확인하고 SHA-256 비교. 두 파일은 659×115 PNG, 42,101 bytes로 바이트까지 동일.
- SHA-256: `6389E638A3A90F94092570AB42660C67848C371A50D8B5E5AF6025688AC29875`.
- 기존 파일이 같다고 가정한 것이 아니라 실제 확인한 결과임. 재생성·재인코딩·색상 변경·이미지 중복 추가 없음. 최종 원본을 보관한 공통 에셋은 `public/brand/team-on.png` 하나.

## 전체 사용처 조사와 변경

| 화면/레이아웃 | 기존 | 적용 |
| --- | --- | --- |
| AuthPage: 로그인·회원가입 | TEAM:ON 텍스트와 색상 span | 공통 BrandLogo |
| 학교 인증 | TEAM:ON 텍스트 | 공통 BrandLogo |
| 초기 프로필 설정 | TEAM:ON 텍스트 | 공통 BrandLogo |
| ProfileLayout: 내 프로필·수정·사용자 프로필 | TEAM:ON 텍스트 | 공통 BrandLogo |
| AppLayout 모집 헤더: 목록·상세·작성·수정·지원자·내 모집글·내 지원·스크랩·차단·알림함 | 기존 PNG | 동일 PNG를 공통 BrandLogo로 참조 |
| 관리자 공통 사이드바 | 기존 PNG | 동일 PNG를 공통 BrandLogo로 참조 |
| 탈퇴 화면 | 미병합 PR #52의 신규 파일 | 이 PR에 복사하지 않음. #52 병합 후 BrandLogo 연결 필요 |
| 설정 홈·알림 설정 및 비밀번호 재설정 | 현재 기준 브랜치에 없음 | 미병합 코드를 가져오지 않음. 병합 후 헤더/독립 로고 사용처를 BrandLogo에 연결하고 재검증 필요 |

- fallback AppLayout의 서비스 소개 이름은 로고로 간주해 교체하지 않음. favicon·브라우저 제목·서비스명은 범위 밖.
- 원래 Link와 `/posts` 이동을 유지. 공통 컴포넌트는 img만 렌더하며 `alt="TEAM:ON"` 사용.
- 기본 너비 148px·760px 이하 124px, 높이 자동·최대 너비 100%. 모집 헤더 기존 CSS 및 관리자 140px/object-fit:contain 스타일을 유지. 원본 색·비율·형태를 변환하는 filter/변형 없음.
- 공유 헤더 변경 영향은 위 표 중 현재 브랜치에 존재하는 화면 전체. 기능·인증·탈퇴 API·상태 처리 코드는 수정하지 않음.

## 변경 분리

- 탈퇴 기능을 feature/front-AUTH-09의 커밋 `7461b60`, PR #52로 먼저 분리. 로고 변경 전 스냅샷·로고 패치·사용자 최종 원본을 Git 밖에 보존하고 기존 stash 3개 유지.
- #52 생성 후 다시 fetch한 최신 origin/dev `4928c06`에서 fix/team-on-logo-unify 생성. #50·#51·#52 모두 미병합이므로 가져오지 않음.
- 로고 범위: components/BrandLogo.tsx·brand-logo.css, AuthPage·ProfileLayout·AppLayout·AdminPage·SchoolVerificationPage·ProfileSetupPage의 로고 사용처 및 이 문서.
- 추가 헤더 범위: SiteHeader·site-header.css를 AppLayout·ProfileLayout에서 재사용. 모집 헤더 기준 여백·간격·글꼴·행간·정렬 통일, 활성 라벨의 굵은 폭 예약, root scrollbar-gutter:stable 적용. NotificationLink는 라벨 폭 예약을 위한 마크업만 변경하며 경로·배지 로직 유지. 본문·인증·프로필 기능 변경 없음. HEADER-ALIGNMENT.md에 영향 화면과 미검증 시각 항목 기록.
- App.tsx 탈퇴 라우트·WithdrawPage·features/withdrawal·탈퇴 검사·WITHDRAWAL 문서는 이 PR에 없음.
- #52 병합 후 WithdrawPage의 직접 PNG를 BrandLogo로 바꾸는 import/마크업 연결 필요. 독립 auth-page 레이아웃이므로 모집 공통 헤더 변경만으로 이 연결이 자동 적용되지는 않음. 기존 직접 PNG도 동일한 최종 원본임.
- #50 병합 후 설정 헤더, #51 병합 후 독립 PasswordResetPage 로고도 확인·연결 필요. 미병합 기능 파일을 복제하지 않음.
- 관련 파일만 커밋·일반 푸시하고 별도 dev 대상 fix PR로 검토. 원격 병합 없음.

## 검증과 시각 확인 주소

- 최종 build·lint·git diff --check 및 staged diff 검사 통과. 공통 인증 16개·모집 12개·신고/차단 및 프로필 연결 12개·관리자 17개, 총 57개 검사 통과. 탈퇴 검사는 PR #52의 38개 검증에 포함되며 이 브랜치로 복사하지 않음. 로고·헤더 변경을 실제 API 검증으로 간주하지 않음.
- 소스 전체 재검색: 직접 만든 TEAM/span 로고 제거, 이미지 경로를 BrandLogo 한 곳에서만 정의.
- 브라우저 직접 시각 확인은 미검증. 사용자에게 요청할 주소(기본 origin `http://localhost:5173`):
  - `/login`, `/signup`, `/school-verification`, `/profile/setup`
  - `/me/profile`, `/me/profile/edit`, `/users/:userId` (실제 사용자 ID)
  - `/posts`, `/posts/:postId`, `/posts/new`, `/posts/:postId/edit`, `/posts/:postId/applicants` (실제 모집글 ID)
  - `/me/posts`, `/me/applications`, `/me/scraps`, `/me/blocks`, `/notifications`
  - `/admin` 및 관리자 하위 메뉴 (기존 로그인 권한 필요)
  - `/settings/withdraw`는 #52 병합 후, 설정 경로와 재설정 경로는 해당 기능 PR 병합 후 후속 확인
- 각 화면에서 데스크톱/모바일 크기·줄바꿈·원본 색상·이미지 찌그러짐 없음·키보드 포커스·기존 홈 이동 확인. 새 테스트 계정·데이터는 만들지 않음.
- 사용자의 탈퇴 동작 확인 완료는 PR #52의 WITHDRAWAL 문서에 유지하며, 전체 로고 화면이나 서버 후처리 검증 완료로 확대하지 않음.
