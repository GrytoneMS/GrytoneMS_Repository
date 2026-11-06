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
│   ├── config/              # 설정
│   └── error/               # ErrorCode, BusinessException, 공통 예외 처리
└── domain/                  # 기능별 폴더 (기능 명세서 영역 단위)
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

## 아직 없는 것

- 로그인·토큰·권한 체크 (공통 기반 다음 작업). 지금 `/api/admin/**`은 누구나 호출할 수 있습니다.
- 커서 페이지네이션 공통 클래스
- DB 설계서 기준 `schema.sql`
