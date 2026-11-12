# 모집글 검색·필터 (LIST-03·04)

## 기준과 작업 범위

- `origin/dev` `a146ac5`에서 `feature/front-LIST-03`을 생성했습니다. 시작 시 작업 트리는 깨끗했습니다. 기존 `fix/front-posts-integration`의 `df68a6a`, `0caed4c`, `52842da`는 해당 브랜치에 그대로 보존합니다.
- 기존 브랜치를 통째로 병합하지 않았습니다. 목록 카드에서 null 학교명 뒤 구분자를 없애는 표시 보완, 목록 작성자 nullable 타입, 네트워크 오류 안내만 선별 반영했습니다. 상세 화면 수정과 과거 연동 기록은 가져오지 않았습니다.
- 기능 명세 5쪽은 필터 LIST-03·검색 LIST-04, 다중 선택의 OR/필터 간 AND, 최근 검색어 10개를 규정합니다. 1차 API 명세 5쪽은 검색·필터 상세를 2차로 예고하므로 실제 파라미터는 최신 `PostController`, `PostSearchCondition`, `PostService.addFilters`, `PostSpecs`, `PostPeriod`를 기준으로 대조했습니다.
- Figma [S04](https://www.figma.com/design/TQAHXMAyHdCqU5aV3qBmhW/?node-id=6-175)를 읽기 전용으로 확인했습니다. 기존 목록 스타일·Button·Input·카드를 재사용하며 검색 아이콘 원본만 `public/posts/search.svg`에 내려받았습니다(20×20). 스크랩·신고·지원 동작은 추가하지 않았습니다.
- AppLayout·공통 API 클라이언트·Button/Input·공통 토큰·패키지 의존성·인증 및 다른 담당 화면은 수정하지 않았습니다. 기능별 `posts/api.ts` 확장은 필터 파라미터와 실제 학교 조회를 위해, `source.ts` 확장은 조건 전달을 위해 필요합니다. `model.ts`는 목록 작성자의 null 계약만 보완했습니다.

## 요청 계약

| 조건 | API 파라미터 | 선택·기본값 |
| --- | --- | --- |
| 모집 유형 | `category` | 다중, CONTEST / COMPETITION / PROJECT / STUDY |
| 모집 역할 | `roleCode` | 다중, PLANNING / DESIGN / DEV / VIDEO / MARKETING / PRESENT / ETC |
| 팀장 학교 | `schoolId` | 다중, 실제 `GET /api/schools`의 활성 학교만 선택지로 제공 |
| 지역·모임 장소 | `locationCode` | 다중, CHEONAN_TERMINAL / CHEONAN_ASAN_STN / DUJEONG_STN / ONYANG_STN / CAMPUS / CUSTOM |
| 진행 방식 | `meetingType` | 다중, OFFLINE / ONLINE / HYBRID |
| 활동 기간 | `period` | 단일, 미선택이 기본. WEEK / MONTH / THREE_MONTHS / OVER_THREE_MONTHS |
| 모집 중만 | `openOnly` | 기본 true. 끄면 false를 명시적으로 전송 |
| 검색 | `keyword` | 제목·소개·행사명, trim 후 2자 이상. 공백만 입력하면 검색 해제 |
| 정렬 | `sort` | LATEST 기본 / DEADLINE |

배열은 `category=PROJECT&category=STUDY`처럼 같은 이름을 반복합니다. `region=CHEONAN` 같은 별도 지역 필터는 서버에 없어 만들지 않았습니다. 장소 상세 문자열을 검색하거나 임의 코드값을 전송하지 않습니다. 활동 기간은 서버의 종료일−시작일 범위인 0~7일, 0~31일, 0~92일, 93~100000일을 각각 사용합니다.

URL에 검색·필터·정렬을 보존하고 새로고침·뒤로가기·상세에서 목록 복귀 시 복원합니다. 조건이 바뀌면 결과 컴포넌트와 loader를 교체해 목록·커서를 초기화합니다. 이전 loader의 AbortSignal과 요청 버전으로 늦은 결과/오류를 무시합니다. 더 보기는 같은 조건과 서버 opaque cursor를 유지하며 중복 요청을 막습니다. 첫 페이지 오류와 추가 페이지 오류를 분리하고, 추가 오류 시 기존 결과를 보존합니다. 실패에서 mock으로 자동 전환하지 않습니다.

필터 초기화는 검색어·정렬을 유지하고 필터와 모집 중 기본값을 복구합니다. 빈 결과의 검색·필터 초기화는 검색어도 지우며 정렬은 유지합니다. 최근 검색어는 브라우저 localStorage에 최대 10개 저장하고 개별/전체 삭제가 가능합니다. 저장이 막히면 현재 탭 메모리에서만 유지합니다. 인증 토큰 저장과는 관계없습니다.

학교 API의 빈 응답에는 “등록된 학교가 없습니다”를 표시합니다. URL에 남은 학교 ID는 제거 가능한 ID 칩으로 표시하며 가짜 학교 이름이나 선택지를 만들지 않습니다. 학교 조회는 독립 로딩·오류·재시도를 제공합니다. 명시적인 개발용 mock에서는 학교 ID 연결을 지원하지 않는다고 안내하며 실제 학교 선택지로 대체 데이터를 넣지 않습니다.

## 검증 결과 (2026-11-12)

- 자동 검증: 빌드·린트 통과, `check-post-filters.mjs` 14개, 기존 `check-posts.mjs` 11개, `check-post-create.mjs` 14개로 총 39개 통과. 요청 가로채기·상태/정적 렌더 검증으로 실제 브라우저 검증과 구분합니다.
- 새 검증: 반복 배열과 단일 값·URL 왕복/초기화·opaque cursor·조건 유지·중복 더 보기 방지·늦은 응답/오류 무시·추가 로딩 중 재조회·오류 재시도·학교 빈 응답/오류·최근 검색어·선택값 정적 렌더.
- 실제 통신: 프론트 `getSchools()` → 빈 배열. `source.loadPosts()`로 기본·키워드·유형 다중·역할 다중·장소 다중·진행 방식 다중·활동 기간 네 가지·openOnly=false·마감순 복합 조건 총 12개 요청이 모두 정상 빈 페이지를 반환했습니다. fetch를 가로채지 않았습니다.
- 직접 HTTP: 반복 category 조건은 200, 1자 검색어는 400 INVALID_INPUT. 두 응답의 CORS 허용 Origin은 `http://localhost:5173`입니다.
- 기존 Windows Vite(5173)와 WSL 백엔드(8080)를 재사용했습니다. Vite의 변환 소스가 현재 검색·필터 코드를 제공하고 `VITE_POSTS_MOCK=false`, `VITE_API_BASE_URL=http://localhost:8080`인 것을 확인했습니다. 현재 backend 트리는 기존 실행 기준 `a146ac5`와 동일하므로 재시작하지 않았습니다.
- 사용자 브라우저 확인 완료: 사용자가 검색·필터 화면을 직접 확인하고 문제가 없다고 알려주었습니다. 에이전트의 브라우저 연결 호스트는 사용 불가였으므로 사용자 확인 결과로 기록합니다. 개별 기기·뷰포트와 세부 체크 항목의 확인 범위를 추가로 추정하지 않습니다.
- **미검증:** 실제 API는 정상 빈 목록 응답까지 확인했습니다. DB·학교 목록이 비어 있어 데이터가 필요한 필터링/정렬 결과, 학교 선택 결과, 기존 글 상세 성공, 실제 다음 페이지는 확인하지 못했습니다. 사용자 화면 확인을 이러한 데이터 검증의 완료로 간주하지 않습니다.
- DB 데이터 삭제·삽입, backend 기능 수정은 하지 않았습니다. 사용자 승인에 따라 검색·필터 변경을 커밋·푸시하고 dev 대상 PR을 생성하며, PR 병합과 다음 기능 개발은 하지 않습니다. 실행 중인 서버도 유지합니다.

## 직접 확인

- 기본: http://localhost:5173/posts
- 복합 조건: http://localhost:5173/posts?keyword=해커톤&category=PROJECT&category=STUDY&roleCode=DEV&meetingType=ONLINE&period=MONTH&openOnly=false&sort=DEADLINE
- 1자 검증: http://localhost:5173/posts?keyword=가

사용자 브라우저 확인은 완료했습니다. 다음은 추가 기기 확인이나 데이터가 준비된 뒤 재확인할 체크리스트입니다(PC와 모바일 320·375·768px 권장).

1. 1자 검색은 안내, 2자 이상은 검색 실행, 검색어 지우기는 검색 해제. 최근 검색어 선택·개별 삭제·전체 삭제.
2. 각 메뉴 다중 체크와 활동 기간 단일 선택, 모집 중 토글, 선택 칩 해제. 학교는 현재 빈 목록 안내만 표시.
3. 필터 초기화는 검색·정렬 유지, 빈 결과의 검색·필터 초기화는 검색까지 해제.
4. 조건 변경 → URL 갱신 → 새로고침·뒤로가기 복원. 데이터가 생기면 더 보기 중 조건을 바꿔 이전 결과가 끼지 않는지 확인.
5. 필터 메뉴·칩·검색 버튼이 잘리지 않고 가로 스크롤이 없는지, Tab/Space/Enter로 조작하고 Escape로 필터를 닫을 수 있는지 확인.

```powershell
cd frontend
npm.cmd run build
npm.cmd run lint
node scripts/check-post-filters.mjs
node scripts/check-posts.mjs
node scripts/check-post-create.mjs
```
