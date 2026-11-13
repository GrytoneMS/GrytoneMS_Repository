# Backend (Spring Boot)

## 준비물

- JDK 17 이상 (17, 21 모두 가능)
- Docker Desktop (로컬 MySQL·Redis용)
- IntelliJ IDEA 추천 (Lombok 플러그인 켜기, `Settings → Build → Compiler → Annotation Processors → Enable` 체크)

## 처음 실행하기

```bash
cd backend

# 1) MySQL 8.0 + Redis 띄우기
docker compose up -d

# 2) 서버 실행 (Windows는 gradlew.bat bootRun)
./gradlew bootRun
```

`http://localhost:8080/api/schools` 에 접속해서 아래처럼 나오면 성공입니다.

```json
{ "success": true, "data": [] }
```

- DB 접속 정보 기본값은 `docker-compose.yml`과 같습니다 (DB `teamup`, 계정 `teamup` / `teamup`).
- 값을 바꾸고 싶으면 `.env.example`을 `.env`로 복사해 수정하고, 같은 값을 실행 환경 변수로도 넣어 주세요. `.env`는 커밋하지 않습니다.
- 테이블은 지금 `ddl-auto: update`라 엔티티를 만들면 자동 생성됩니다. DB 설계서 기준 `schema.sql`이 준비되면 `validate`로 바꿉니다.

## 패키지 구조

```
com.grytonems.backend
├── global/                  # 모든 기능이 같이 쓰는 것
│   ├── common/              # ApiResponse(성공 응답), CursorPage(목록 페이지), BaseTimeEntity(created_at·updated_at)
│   ├── config/              # 설정 (CORS 등)
│   ├── error/               # ErrorCode, BusinessException, 공통 예외 처리
│   ├── file/                # 업로드 파일 저장(FileStorage), 프로필 이미지 공개 주소(/files/profiles/**)
│   ├── mail/                # 메일 발송 (서버 미설정이면 로그로 출력)
│   └── security/            # 로그인 토큰(JWT), 주소별 권한(SecurityConfig)
└── domain/                  # 기능별 폴더 (기능 명세서 영역 단위)
    ├── user/                # 회원가입·로그인·내 정보·닉네임 변경·비밀번호 재설정·탈퇴·이용 정지 (AUTH-01·03·04·05·09, ADMN-04)
    ├── verification/        # 학교 이메일 인증·서류 인증·관리자 심사·학기 재인증 (AUTH-06~08, ADMN-03)
    ├── post/                # 모집글 (POST)
    ├── application/         # 지원·수락·거절·연락 수단 (APPL)
    ├── profile/             # 프로필: 기본 정보·이미지·희망 역할·보유 기술·이력 항목·활동 이력·완성도·타인·지원자 프로필 (PROF-01~12, APPL-02)
    ├── notification/        # 알림 생성·알림함·알림 설정 (NOTI-01~03)
    ├── scrap/               # 스크랩·내 스크랩 (SCRP-01, 02)
    ├── report/              # 신고·신고 3건 자동 숨김·관리자 신고 처리 (SAFE-01, 02, ADMN-02)
    ├── block/               # 차단·해제·차단 목록, 다른 기능에서 쓰는 BlockPolicy (SAFE-03, LIST-06)
    └── school/              # 예시: 학교 목록·등록·수정 (AUTH-06, ADMN-05)
        ├── School.java              # Entity
        ├── SchoolRepository.java    # DB 접근
        ├── SchoolService.java       # 규칙·예외 처리
        ├── SchoolController.java    # URL, 요청·응답
        └── dto/                     # 요청·응답 형식
```

새 기능은 `domain/` 아래에 영역 폴더를 만들어 시작합니다. (`user`, `profile`, `post`, `application`, `scrap`, `notification`, `report` …)

## API 하나 만드는 순서

`domain/school`을 그대로 따라 하면 됩니다. 파일마다 `[1]~[5]` 주석으로 역할을 적어 뒀어요.

1. **Entity** — DB 설계서의 테이블을 보고 컬럼 이름·길이·NOT NULL·UNIQUE를 똑같이 옮깁니다. `BaseTimeEntity`를 상속하면 `created_at`, `updated_at`은 자동입니다.
2. **Repository** — `JpaRepository<엔티티, Long>`를 상속하고, 필요한 조회 메서드를 이름 규칙으로 추가합니다.
3. **DTO** — 요청은 `record` + 검증 어노테이션(`@NotBlank`, `@Size` …)으로 기능 명세서의 글자 수 제한을 적고, 응답은 내려줄 값만 담습니다.
4. **Service** — 기능 명세서의 "상세 규칙"과 "예외·오류 처리"를 여기서 구현합니다. 규칙 위반은 `ErrorCode`에 한 줄 추가하고 `throw new BusinessException(ErrorCode.XXX)`.
5. **Controller** — URL을 정하고, 요청 본문에 `@Valid`, 응답은 `ApiResponse.ok(...)`로 감쌉니다.

## 응답 형식

성공

```json
{ "success": true, "data": { } }
```

실패

```json
{
  "success": false,
  "code": "INVALID_INPUT",
  "message": "입력값을 확인해 주세요",
  "fieldErrors": [ { "field": "emailDomain", "message": "도메인 형식이 아니에요 (예: dankook.ac.kr)" } ]
}
```

프론트는 `code`로 분기합니다 (`UNAUTHORIZED` → 로그인 화면, `UNVERIFIED` → 학교 인증 화면). `fieldErrors`는 입력 검증 실패일 때만 들어갑니다.

## 로그인 사용자 꺼내기

로그인한 사용자는 컨트롤러 파라미터에 `@AuthenticationPrincipal AuthUser loginUser`를 적어 받습니다.

```java
@PostMapping("/api/posts")
public ApiResponse<PostCreateResponse> create(@AuthenticationPrincipal AuthUser loginUser,
                                              @Valid @RequestBody PostCreateRequest request) {
    return ApiResponse.ok(postService.create(loginUser.id(), request));
}
```

- `loginUser.id()`: 사용자 id, `loginUser.status()`: 인증 상태, `loginUser.role()`: USER / ADMIN
- 토큰이 없으면 컨트롤러까지 오기 전에 401로 막히므로 `loginUser`가 null인지 확인할 필요 없습니다. (비회원도 들어오는 주소에서는 null일 수 있어요.)

## 알림 보내기

다른 기능에서 알림을 보낼 때는 `NotificationService`를 주입받아 한 줄로 호출합니다.

```java
notificationService.notify(받는사람Id, NotificationType.ACCEPTED, NotificationTargetType.POST, postId,
        "축하해요! '" + postTitle + "' 팀에 합류했어요. 연락 수단을 확인하세요");
```

- 호출한 쪽의 트랜잭션에 함께 묶여서, 그 작업이 실패해 취소되면 알림도 같이 취소됩니다.
- 문구는 기능 명세서 6장 "알림 목록"의 문구 예시를 따릅니다. 여러 명에게 보낼 때는 `notifyAll`.
- 앱 푸시는 1차 범위에서 빠져 알림함에만 쌓입니다.

## 모집글 숨김 (SAFE-02, ADMN-02)

숨김·복구는 `PostModerationService`의 `hide(postId)`, `restore(postId)` 한곳에서 처리합니다. 관리자가 직접 `PATCH /api/admin/posts/{id}/hide`, `/restore`로 호출하거나, 신고(`ReportService`)가 미처리 신고 3건이 쌓이면 `hide`, 관리자가 "문제없음"으로 처리하면 `restore`를 호출합니다.

- 숨김 글은 목록·상세에서 빠지고 팀장 본인에게도 404입니다. 모집 중인 글 3개 제한에서는 빠집니다.
- 숨기면 대기 중인 지원은 마감되고, 팀장에게 숨김 알림(이동 대상 없음), 대기 지원자에게 마감 알림이 갑니다.
- 복구하면 숨김 전 모집 상태로 돌아가고 팀장에게 복구 알림이 갑니다. 숨김 때 마감된 지원은 되돌리지 않습니다.

## 정해진 시각에 도는 작업 (한국 시간)

`PostDeadlineScheduler`에 모아 둡니다.

| 작업 | 시각 | 내용 |
| --- | --- | --- |
| 자동 마감 (POST-06) | 매시 정각 + 서버 시작 직후 | 마감일이 지난 모집중 글을 수동 마감과 똑같이 처리 (대기 지원 마감 + 대기자 알림) |
| 마감 D-1 알림 | 매일 오전 9시 | 내일 마감인 모집중 글의 팀장(`DEADLINE_SOON`)과 스크랩한 사람(`SCRAP_DEADLINE_SOON`)에게 알림 |
| 정지 기간 종료 (ADMN-04) | 매시 정각 10초 | 기간이 끝난 이용 정지를 풀어 이전 상태(인증·미인증)로 되돌림 |
| 알림 정리 (NOTI-02) | 매일 새벽 3시 | 30일 지난 알림 삭제 |
| 대기 지원 리마인드 (APPL-12) | 매일 오전 9시 | 3일 넘게 대기 중인 지원이 있는 글의 팀장에게 글마다 `APPLICATION_REMINDER` 한 건 (같은 지원은 한 번만) |
| 학기 재인증 요청 (AUTH-08) | 3월 1일·9월 1일 오전 9시 | 학기 시작 전에 인증한 회원에게 `REVERIFY_REQUIRED` (기한 3/14·9/14) |
| 재인증 기한 경과 (AUTH-08) | 매일 자정 + 서버 시작 직후 | 기한까지 다시 인증하지 않은 회원을 미인증으로 (글·지원은 그대로) |

테스트에서는 `app.scheduling.enabled: false`로 꺼져 있고, 스케줄러 메서드에 날짜를 넘겨 직접 호출합니다.

## 주소별 권한

`global/security/SecurityConfig.java`에서 한곳에 관리합니다.

| 구분 | 주소 | 실패 시 |
| --- | --- | --- |
| 비회원 가능 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/password-reset(/confirm)`, `GET /api/schools`, `GET /api/posts`, `GET /api/posts/{id}`, `GET /files/profiles/**` | - |
| 학교 인증 회원만 | `POST /api/posts`, `POST /api/posts/{id}/applications`, `PUT /api/posts/{id}/scrap`, `POST /api/reports` | 미인증 403 `UNVERIFIED`, 정지 403 `SUSPENDED` |
| 관리자만 | `/api/admin/**` | 403 `FORBIDDEN` |
| 그 밖의 `/api/**` | 로그인만 하면 가능 | 401 `UNAUTHORIZED` |

새 API가 비회원용이거나 인증 회원 전용이면 `SecurityConfig` 목록에 주소를 추가해 주세요. 그 외에는 따로 할 일이 없습니다.

## 테스트

```bash
./gradlew test
```

MySQL·Redis 없이 메모리 DB(H2)로 돌아갑니다 (`src/test/resources/application-test.yml`). PR을 올리면 GitHub Actions가 같은 테스트를 자동으로 돌리고, PR 화면 아래 Checks에 결과가 나옵니다.

## 학교 이메일 인증 (AUTH-06)

인증 코드는 Redis에 5분간 저장되므로 `docker compose up -d`로 Redis가 떠 있어야 합니다.

**메일 서버를 설정하지 않으면** 메일 대신 서버 로그에 코드가 찍힙니다. 로컬 개발·시연은 이걸로 충분해요.

```
[메일 서버 미설정: 로그로 대신 출력] to=student@dankook.ac.kr subject=[천안·아산 팀원 모집] 학교 인증 코드
학교 인증 코드: 482913
```

**실제 메일을 보내려면** (Gmail 예시) 서버 실행 환경 변수에 아래를 넣습니다. 비밀번호는 Google 계정의 "앱 비밀번호"(16자리)를 씁니다.

```
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=보내는계정@gmail.com
SPRING_MAIL_PASSWORD=앱비밀번호16자리
```

**인증할 학교 등록:** 학교 목록은 비어 있습니다. 관리자 계정으로 `POST /api/admin/schools`에 학교명·캠퍼스·학생 이메일 도메인을 등록해야 그 학교로 인증할 수 있어요. 관리자 계정은 지금 DB에서 `users.role`을 `ADMIN`으로 바꿔 만듭니다.

## 파일 업로드 (PROF-01, AUTH-07)

프로필 이미지와 학교 인증 서류는 서버 디렉터리에 저장합니다 (jpg·png, 5MB 이하).

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `UPLOAD_DIR` | `uploads` | 저장 폴더. 배포 서버에서는 지워지지 않는 경로로 지정 |
| `UPLOAD_PUBLIC_URL` | `http://localhost:8080/files` | 프로필 이미지 주소 앞부분 (배포 주소로 바꾸기) |
| `FRONTEND_URL` | `http://localhost:5173` | 비밀번호 재설정 메일의 링크 주소 앞부분 |

- `profiles/`: `/files/profiles/**`로 누구나 볼 수 있습니다.
- `documents/`: 공개하지 않고, 관리자만 `GET /api/admin/verifications/{id}/document`로 받습니다. 승인·반려하면 바로 지웁니다.

## 아직 없는 것

- 앱 푸시 (알림 설정은 저장·조회만 됨)
- 9개 학교의 학생 이메일 도메인 확인 및 등록
- DB 설계서 기준 `schema.sql`
