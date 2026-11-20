# 설정 홈 · 알림 설정 (NOTI-03)

## 기준과 범위

- 최신 `origin/dev=ea738fa`에서 `feature/front-NOTI-03` 생성. 시작 시 작업 트리는 깨끗했고 SAFE/AUTH 기존 브랜치·커밋·stash 3개를 보존.
- #48(신고·차단), #49(알림함)는 작업 시작 시 모두 OPEN/미병합. 미병합 코드는 가져오지 않음.
- 기존 설정 홈/설정 조회·저장 UI가 없어 `/settings`와 `/settings/notifications` 추가. 마이페이지의 설정 링크만 연결. 알림함·읽음·배지·비밀번호 재설정·회원 탈퇴는 구현하지 않음.
- 공통 변경은 App의 두 라우트, AppLayout의 두 경로 레이아웃 선택, MyProfilePage의 설정 링크로 제한. 공통 Button·API 클라이언트·인증 구현·백엔드는 변경하지 않음.

## 디자인 확인 범위

- 저장소에 기록된 Figma 파일 `TQAHXMAyHdCqU5aV3qBmhW`를 읽기 전용으로 조회. 파일 페이지 목록은 받았지만 설정 화면 메타데이터 조회는 Starter MCP 호출 한도로 거절됨. 화면 상세/스크린샷을 확인했다고 간주하지 않음.
- 제공 Downloads 폴더에는 스크랩·관리자 PDF만 있으며 설정 PDF는 찾지 못함. Figma 파일과 PDF를 수정하지 않음.
- 디자인을 확인할 수 없어 기존 모집 화면의 레이아웃·흰 패널·둥근 테두리·Button·색상 변수를 재사용. 알림 설정은 변경 후 저장 버튼 방식. 시간 선택 UI나 가짜 디자인 자산은 추가하지 않음. PC/모바일 실화면의 디자인 일치는 후속 확인 필요.

## 서버 계약과 방해 금지의 실제 의미

- NotificationController/NotificationSettingDto/NotificationSettingService/NotificationSetting/NotificationService 및 SecurityConfig 대조.
- GET/PUT `/api/users/me/notification-settings`: `applyEnabled`, `deadlineEnabled`, `quietHoursEnabled` 세 boolean. PUT에는 모두 필수. GET에서 서버가 행 없는 사용자의 기본값을 반환할 수 있지만 프론트 조회 실패를 기본값으로 대체하지 않음.
- 로그인만 필요하며 학교 인증 제한 없음. 미인증 계정으로 실제 저장도 확인.
- 서버 NotificationSetting 주석에 따르면 세 설정은 앱 푸시용이며 현재 1차에서는 저장·조회만 지원. NotificationService는 이를 필터로 사용하지 않으므로 알림함은 설정에 상관없이 계속 쌓임.
- 서버의 일반 시간대는 Asia/Seoul(한국시간). 그러나 방해 금지 시작/종료 시각, 해당 시간대 변환, 실제 알림 억제 로직은 구현돼 있지 않음. 따라서 '22~07시' 같은 임의 시간 구간이나 시간 선택 기능을 만들지 않으며, 현재는 사용 여부만 저장된다고 안내.

## 상태와 인증

- 기존 `useAuth`, 공통 session/scope, `store.execute`, 공통 AbortSignal 재사용. 별도 토큰 저장·감시 없음. 확인 전/비로그인/만료 상태는 기존 AuthStatus로 구분.
- 조회 중·실패·재시도, 편집·저장 중·성공·실패 구현. 서버 조회 성공 전에는 편집/저장 불가. 세 boolean의 런타임 검증, 서버 값 기반 변경·전체 PUT.
- 저장 중에는 입력/저장을 잠가 동일 tick 중복 저장과 저장 응답이 새 입력을 덮는 문제를 방지. 조회 완료 후 별도 조회로 편집값을 덮지 않음. 실패 시 draft와 이전 saved 유지. 네트워크 응답 유실은 저장 가능성을 포함해 '결과를 확인하지 못함'으로 안내.
- 계정 전환/로그아웃/인증 확인 중에는 개인 설정 화면을 제거. 이전 Controller 폐기, 세션 동일성·요청 세대·취소 신호로 늦은 응답 무시.

## 검증

- 빌드·lint·`git diff --check` 통과.
- `check-notification-settings.mjs` 8개: GET/세 값 PUT/공통 Bearer, 잘못된 응답, 조회 실패 저장 차단/재시도, 저장 실패 입력 유지/중복 방지, 늦은 응답, 401/403, 계정 전환, 비로그인/미인증 정적 화면.
- 기존 `check-auth-session.mjs` 16개, `check-posts.mjs` 12개 통과. 총 36개 자동 검사. 요청 가로채기/Controller·세션/정적 렌더이며 실제 브라우저 검증과 구분.
- 실제 로컬 teamup/MySQL/Redis/백엔드의 loopback·ddl-auto=validate·정기 배치 OFF 확인. 기존 테스트 계정 두 개(미인증·인증)로 최종 프론트 서비스와 Controller를 실제 서버에 연결해 GET→세 값 변경 PUT→GET 확인 후 원래 값 PUT→GET 복구 확인. 무토큰 GET 401도 확인.
- DB 직접 수정·계정 추가·권한 변경 없음. 테스트 계정·토큰·환경·로그·백업은 Git 밖에 유지.
- 브라우저 도구는 `The browser access host is unavailable` 반환. PC/모바일 배치·키보드 조작·실제 화면 계정 전환은 미검증. 실제 API 성공을 프론트 전체 성공으로 간주하지 않음.

## 수동 체크리스트

1. `/me/profile` → 설정 → `/settings` → 알림 설정 `/settings/notifications` 진입.
2. 비로그인·인증 확인 중에 개인 설정이 표시되지 않는지 확인. 로그인한 미인증 회원도 진입/저장할 수 있어야 함.
3. 서버 값과 세 체크박스 비교 → 하나 변경 → 저장 → 성공 안내 → 새로고침 후 유지 확인. 테스트 후 원래 선택으로 저장.
4. Network Offline에서 최초 조회 실패/재시도 및 저장 차단 확인. 조회 후 편집·저장 중 실패시키면 변경값이 유지되는지 확인. 저장 결과가 불명확하면 서버 재조회로 최종 상태 확인.
5. 저장 버튼 연속 입력, 저장 중 체크박스 잠금, 로그아웃/다른 계정 로그인 시 이전 선택·오류·성공 안내 제거 확인.
6. PC/모바일 폭에서 가로 넘침, 준비 중 메뉴 표시 확인. Tab/Shift+Tab으로 링크·체크박스·저장 버튼 접근, Space로 체크박스, Enter로 저장, 포커스 테두리 확인.

## 병합 후 연결과 남은 작업

- 최종 fetch에서 #48·#49 모두 MERGED 확인. 최신 `origin/dev=4928c06`을 fast-forward 반영하고 `/me/blocks` 링크 활성화.
- 변경 복원 중 App.tsx·AppLayout.tsx 충돌은 양쪽 라우트와 NotificationProvider·NotificationLink·알림함·배지·공통 메뉴를 보존해 해결. 알림 설정의 저장은 알림함 수신을 차단하지 않는 안내 유지.
- 비밀번호 재설정·회원 탈퇴는 이번 메뉴에서 준비 중으로만 표시하고 별도 후속 작업으로 남김.
- 설정 디자인 원본 확인, 브라우저 PC/모바일/키보드 검증 필요. 사용자에게 명시적으로 확인받은 수동 검증 항목은 없음. 위 수동 체크리스트는 전부 미검증.
- 최종 자동 검증: build·lint, 설정 8개·공통 인증 16개·모집글 12개·신고/차단 12개 검사와 `git diff --check`. 실제 API 검증은 위에 기록된 기존 작업의 조회→저장→재조회 및 원래 설정 복원 기록이며 이번 정리 단계에서 재실행한 결과와 구분.
- 기존 stash 3개 보존. backend 직접 수정·강제 푸시·원격 병합·후속 기능 개발 없음.
