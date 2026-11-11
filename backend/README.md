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
│   ├── common/              # ApiResponse(성공 응답), BaseTimeEntity(created_at·updated_at)
│   ├── config/              # 설정 (CORS 등)
│   ├── error/               # ErrorCode, BusinessException, 공통 예외 처리
│   └── security/            # 로그인 토큰(JWT), 주소별 권한(SecurityConfig)
└── domain/                  # 기능별 폴더 (기능 명세서 영역 단위)
    ├── user/                # 회원가입·로그인·내 정보 (AUTH-01, AUTH-04)
    └── school/              # 예시: 학교 목록·등록 (AUTH-06, ADMN-05)
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

## 주소별 권한

`global/security/SecurityConfig.java`에서 한곳에 관리합니다.

| 구분 | 주소 | 실패 시 |
| --- | --- | --- |
| 비회원 가능 | `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/schools`, `GET /api/posts`, `GET /api/posts/{id}` | - |
| 학교 인증 회원만 | `POST /api/posts`, `POST /api/posts/{id}/applications` | 미인증 403 `UNVERIFIED`, 정지 403 `SUSPENDED` |
| 관리자만 | `/api/admin/**` | 403 `FORBIDDEN` |
| 그 밖의 `/api/**` | 로그인만 하면 가능 | 401 `UNAUTHORIZED` |

새 API가 비회원용이거나 인증 회원 전용이면 `SecurityConfig` 목록에 주소를 추가해 주세요. 그 외에는 따로 할 일이 없습니다.

## 테스트

```bash
./gradlew test
```

MySQL·Redis 없이 메모리 DB(H2)로 돌아갑니다 (`src/test/resources/application-test.yml`). PR을 올리면 GitHub Actions가 같은 테스트를 자동으로 돌리고, PR 화면 아래 Checks에 결과가 나옵니다.

## 아직 없는 것

- 학교 인증 (AUTH-06·07). 지금은 가입하면 미인증이라 글쓰기·지원이 막힙니다. 로컬에서 테스트하려면 DB에서 `users.status`를 `VERIFIED`로 바꿔 주세요.
- 커서 페이지네이션 공통 클래스
- DB 설계서 기준 `schema.sql`
