package com.grytonems.backend.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/**
 * 모집글 API 통합 테스트 (POST-01, POST-04, LIST-01~05).
 * 목록·상세용 글은 저장소로 직접 만들고(마감일 지난 글 등 API로 못 만드는 경우 포함), 작성은 실제 API를 호출합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostApiTest {

    private static final String DESCRIPTION = "아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.";
    private static final String CONTACT = "https://open.kakao.com/o/test";
    private static final String MESSAGE = "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;

    School dankook;
    School hoseo;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    Long aliceId;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        dankook = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        hoseo = schoolRepository.save(School.builder()
                .name("호서대학교").campus("아산캠퍼스").emailDomain("hoseo.edu").region(Region.ASAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장", dankook);
        leaderId = idOf("leader@example.com");
        aliceToken = signupVerified("alice@example.com", "앨리스", hoseo);
        aliceId = idOf("alice@example.com");
        bobToken = signupVerified("bob@example.com", "밥이", dankook);
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        applicationRepository.deleteAll();
        eventRepository.deleteAll();
        postRoleRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    // ===== helpers =====

    private String signup(String email, String nickname) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", "abcd1234", "nickname", nickname,
                "agreeTerms", true, "agreePrivacy", true);
        String json = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private String signupVerified(String email, String nickname, School school) throws Exception {
        String token = signup(email, nickname);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.verify(school, email.replace("@example.com", "@" + school.getEmailDomain()), LocalDateTime.now());
        userRepository.save(user);
        return token;
    }

    private Long idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    /** 글 하나를 저장소로 바로 저장. 바꿀 값만 넘깁니다 */
    private Post savePost(Long leader, String title, PostCategory category, MeetingType meetingType,
                          LocationCode locationCode, LocalDate deadline, int periodDays) {
        LocalDate start = deadline.plusDays(3);
        return postRepository.save(Post.builder()
                .leaderId(leader)
                .title(title)
                .category(category)
                .description(DESCRIPTION)
                .periodStart(start)
                .periodEnd(start.plusDays(periodDays))
                .deadline(deadline)
                .meetingType(meetingType)
                .locationCode(locationCode)
                .meetingFreq(meetingType == MeetingType.ONLINE ? null : "주 1회")
                .contactLink(CONTACT)
                .build());
    }

    private Post savePost(String title) {
        return savePost(leaderId, title, PostCategory.PROJECT, MeetingType.ONLINE, null,
                LocalDate.now().plusDays(7), 30);
    }

    private PostRole saveRole(Post post, RoleCode code, String name, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(name).headcount(headcount).build());
    }

    private ResultActions getAs(String token, String url) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions list(String query) throws Exception {
        return getAs(null, "/api/posts" + query);
    }

    private ResultActions close(String token, Long postId) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/posts/" + postId + "/close");
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Long applyOk(String token, Long postId, Long roleId) throws Exception {
        String json = mockMvc.perform(post("/api/posts/" + postId + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("postRoleId", roleId, "message", MESSAGE, "availableTime", "평일 저녁"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    /** 작성 요청 본문 (온라인 프로젝트). 테스트마다 필요한 값만 덮어씁니다 */
    private Map<String, Object> createBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "충남 청년 아이디어 공모전 팀원 구해요");
        body.put("category", "PROJECT");
        body.put("description", DESCRIPTION);
        body.put("periodStart", LocalDate.now().plusDays(10).toString());
        body.put("periodEnd", LocalDate.now().plusDays(40).toString());
        body.put("deadline", LocalDate.now().plusDays(7).toString());
        body.put("meetingType", "ONLINE");
        body.put("roles", List.of(Map.of("roleCode", "DESIGN", "roleName", "UI 디자인", "headcount", 1)));
        body.put("contactLink", CONTACT);
        return body;
    }

    private ResultActions create(String token, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    // ===== POST-01 모집글 작성 =====

    @Nested
    @DisplayName("모집글 작성 POST /api/posts")
    class Create {

        @Test
        @DisplayName("성공하면 201, 팀장은 로그인한 사용자이고 역할도 함께 저장")
        void success() throws Exception {
            String json = create(leaderToken, createBody())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andReturn().getResponse().getContentAsString();
            Long postId = objectMapper.readTree(json).at("/data/id").asLong();

            Post saved = postRepository.findById(postId).orElseThrow();
            assertThat(saved.getLeaderId()).isEqualTo(leaderId);
            assertThat(saved.getStatus()).isEqualTo(RecruitStatus.OPEN);
            assertThat(postRoleRepository.findAllByPostIdOrderByIdAsc(postId)).hasSize(1);
        }

        @Test
        @DisplayName("공모전은 행사 정보도 저장")
        void contestSavesEvent() throws Exception {
            Map<String, Object> body = createBody();
            body.put("category", "CONTEST");
            body.put("event", Map.of("name", "충남 청년 창업 아이디어 공모전", "host", "충청남도",
                    "officialUrl", "https://example.com/contest",
                    "applyDeadline", LocalDate.now().plusDays(20).toString()));
            String json = create(leaderToken, body)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long postId = objectMapper.readTree(json).at("/data/id").asLong();

            assertThat(eventRepository.findByPostId(postId)).isPresent();
        }

        @Test
        @DisplayName("공모전인데 행사 정보가 없으면 400")
        void contestNeedsEvent() throws Exception {
            Map<String, Object> body = createBody();
            body.put("category", "CONTEST");
            create(leaderToken, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("대면인데 장소가 없으면 400, 마감일이 활동 시작일보다 늦으면 400")
        void dateAndPlaceRules() throws Exception {
            Map<String, Object> offline = createBody();
            offline.put("meetingType", "OFFLINE");
            offline.put("meetingFreq", "주 1회");
            create(leaderToken, offline).andExpect(status().isBadRequest());

            Map<String, Object> lateDeadline = createBody();
            lateDeadline.put("deadline", LocalDate.now().plusDays(15).toString());
            create(leaderToken, lateDeadline).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("모집 중인 글이 이미 3개면 409 POST_LIMIT_EXCEEDED")
        void limitThree() throws Exception {
            for (int i = 0; i < 3; i++) {
                create(leaderToken, createBody()).andExpect(status().isCreated());
            }
            create(leaderToken, createBody())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_LIMIT_EXCEEDED"));
        }

        @Test
        @DisplayName("미인증 회원은 403 UNVERIFIED, 비회원은 401")
        void needsVerified() throws Exception {
            String unverified = signup("new@example.com", "새회원");
            create(unverified, createBody())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
            mockMvc.perform(post("/api/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createBody())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ===== LIST-01·02 목록·정렬 =====

    @Nested
    @DisplayName("목록 GET /api/posts")
    class ListPosts {

        @Test
        @DisplayName("비회원도 볼 수 있고, 카드에 팀장·역할·D-day가 담긴다")
        void card() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);

            list("")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].id").value(post.getId()))
                    .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.data.items[0].dDay").value(7))
                    .andExpect(jsonPath("$.data.items[0].leader.nickname").value("팀장"))
                    .andExpect(jsonPath("$.data.items[0].leader.schoolName").value("단국대학교"))
                    .andExpect(jsonPath("$.data.items[0].roles[0].roleCode").value("DESIGN"))
                    .andExpect(jsonPath("$.data.items[0].roles[0].remaining").value(2))
                    .andExpect(jsonPath("$.data.items[0].contactLink").doesNotExist())
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("기본은 최신순, size로 자르고 nextCursor로 다음 페이지")
        void latestWithCursor() throws Exception {
            Post first = savePost("첫 번째 모집글");
            Post second = savePost("두 번째 모집글");
            Post third = savePost("세 번째 모집글");

            String json = list("?size=2")
                    .andExpect(jsonPath("$.data.items[*].id",
                            contains(third.getId().intValue(), second.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = objectMapper.readTree(json).at("/data/nextCursor").asText();

            list("?size=2&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.items[*].id", contains(first.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("마감 임박순은 마감일 빠른 순이고, 마감일 지난 글은 빠지며, 커서로 이어진다")
        void deadlineSort() throws Exception {
            LocalDate today = LocalDate.now();
            Post far = savePost(leaderId, "마감 먼 모집글", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    today.plusDays(20), 30);
            Post near = savePost(leaderId, "마감 가까운 글", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    today.plusDays(2), 30);
            Post todayPost = savePost(leaderId, "오늘 마감인 글", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    today, 30);
            savePost(leaderId, "이미 지난 모집글", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    today.minusDays(1), 30);

            String json = list("?sort=DEADLINE&size=2")
                    .andExpect(jsonPath("$.data.items[*].id",
                            contains(todayPost.getId().intValue(), near.getId().intValue())))
                    .andExpect(jsonPath("$.data.items[0].dDay").value(0))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = objectMapper.readTree(json).at("/data/nextCursor").asText();

            list("?sort=DEADLINE&size=2&cursor=" + cursor)
                    .andExpect(jsonPath("$.data.items[*].id", contains(far.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("마감된 글은 기본으로 빠지고 openOnly=false면 보인다. 숨김 글은 항상 빠진다")
        void openOnlyAndHidden() throws Exception {
            Post open = savePost("모집 중인 모집글");
            Post closed = savePost("마감된 모집글이에요");
            closed.close();
            postRepository.save(closed);
            Post hidden = savePost("숨김 처리된 모집글");
            ReflectionTestUtils.setField(hidden, "hidden", true);
            postRepository.save(hidden);

            list("").andExpect(jsonPath("$.data.items[*].id", contains(open.getId().intValue())));
            list("?openOnly=false").andExpect(jsonPath("$.data.items[*].id",
                    contains(closed.getId().intValue(), open.getId().intValue())));
        }

        @Test
        @DisplayName("잘못된 정렬·커서·enum 값은 400 INVALID_INPUT")
        void badQuery() throws Exception {
            list("?sort=POPULAR").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            list("?cursor=abc").andExpect(status().isBadRequest());
            list("?sort=DEADLINE&cursor=2026-13-40_1").andExpect(status().isBadRequest());
            list("?category=PARTY").andExpect(status().isBadRequest());
        }
    }

    // ===== LIST-03·04 필터·검색 =====

    @Nested
    @DisplayName("목록 필터·검색 GET /api/posts?...")
    class Filters {

        Post contestOffline;
        Post projectOnline;
        Post studyByAlice;

        @BeforeEach
        void posts() {
            LocalDate deadline = LocalDate.now().plusDays(7);
            contestOffline = savePost(leaderId, "공모전 같이 나가실 분", PostCategory.CONTEST, MeetingType.OFFLINE,
                    LocationCode.CHEONAN_TERMINAL, deadline, 30);
            saveRole(contestOffline, RoleCode.DESIGN, "UI 디자인", 1);
            eventRepository.save(Event.builder().post(contestOffline).name("충남 빅데이터 분석 경진대회")
                    .host("충청남도").officialUrl("https://example.com/bigdata").applyDeadline(deadline)
                    .build());

            projectOnline = savePost(leaderId, "사이드 프로젝트 개발자", PostCategory.PROJECT, MeetingType.ONLINE,
                    null, deadline, 5);
            saveRole(projectOnline, RoleCode.DEV, "백엔드", 2);

            studyByAlice = savePost(aliceId, "앱 출시까지 함께해요", PostCategory.PROJECT, MeetingType.OFFLINE,
                    LocationCode.ONYANG_STN, deadline, 120);
            saveRole(studyByAlice, RoleCode.MARKETING, "마케팅", 1);
        }

        private void expectIds(String query, Post... expected) throws Exception {
            Integer[] ids = Arrays.stream(expected).map(p -> p.getId().intValue()).toArray(Integer[]::new);
            list(query)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(ids.length)))
                    .andExpect(jsonPath("$.data.items[*].id", containsInAnyOrder(ids)));
        }

        @Test
        @DisplayName("모집 유형")
        void category() throws Exception {
            expectIds("?category=CONTEST", contestOffline);
            expectIds("?category=CONTEST&category=PROJECT", contestOffline, projectOnline, studyByAlice);
        }

        @Test
        @DisplayName("역할: 여러 개면 하나라도 있는 글")
        void roleCode() throws Exception {
            expectIds("?roleCode=DEV", projectOnline);
            expectIds("?roleCode=DEV&roleCode=MARKETING", projectOnline, studyByAlice);
        }

        @Test
        @DisplayName("팀장 학교")
        void school() throws Exception {
            expectIds("?schoolId=" + hoseo.getId(), studyByAlice);
            expectIds("?schoolId=" + dankook.getId(), contestOffline, projectOnline);
        }

        @Test
        @DisplayName("진행 방식과 모임 장소, 여러 필터는 AND")
        void meetingAndLocation() throws Exception {
            expectIds("?meetingType=ONLINE", projectOnline);
            expectIds("?locationCode=ONYANG_STN", studyByAlice);
            expectIds("?meetingType=OFFLINE&category=CONTEST", contestOffline);
            expectIds("?roleCode=DEV&category=CONTEST");
        }

        @Test
        @DisplayName("키워드: 제목·소개·행사명에서 찾기, 1글자면 400")
        void keyword() throws Exception {
            expectIds("?keyword=사이드", projectOnline);
            expectIds("?keyword=빅데이터", contestOffline);
            expectIds("?keyword=디자인과", contestOffline, projectOnline, studyByAlice);
            expectIds("?keyword=없는검색어");
            list("?keyword=앱").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    /**
     * 활동 기간 필터는 MySQL 전용 DATEDIFF(종료일, 시작일)를 씁니다.
     * 테스트 DB(H2)에서도 같은 결과가 나오는지 따로 확인합니다.
     */
    @Nested
    @DisplayName("목록 활동 기간 필터 GET /api/posts?period=")
    class PeriodFilter {

        @Test
        @DisplayName("1주 이내·1개월 이내·3개월 이상")
        void period() throws Exception {
            LocalDate deadline = LocalDate.now().plusDays(7);
            Post fiveDays = savePost(leaderId, "닷새짜리 해커톤", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    deadline, 5);
            Post month = savePost(leaderId, "한 달짜리 프로젝트", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    deadline, 30);
            Post long120 = savePost(leaderId, "넉 달짜리 프로젝트", PostCategory.PROJECT, MeetingType.ONLINE, null,
                    deadline, 120);

            list("?period=WEEK").andExpect(jsonPath("$.data.items[*].id", contains(fiveDays.getId().intValue())));
            list("?period=MONTH").andExpect(jsonPath("$.data.items[*].id",
                    containsInAnyOrder(fiveDays.getId().intValue(), month.getId().intValue())));
            list("?period=OVER_THREE_MONTHS").andExpect(jsonPath("$.data.items[*].id",
                    contains(long120.getId().intValue())));
        }
    }

    // ===== LIST-05 상세 =====

    @Nested
    @DisplayName("상세 GET /api/posts/{postId}")
    class Detail {

        @Test
        @DisplayName("비회원: isMine=false, myApplication=null, 연락 링크는 없음")
        void guest() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);

            getAs(null, "/api/posts/" + post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(post.getId()))
                    .andExpect(jsonPath("$.data.description").value(DESCRIPTION))
                    .andExpect(jsonPath("$.data.dDay").value(7))
                    .andExpect(jsonPath("$.data.roles[0].id").value(design.getId()))
                    .andExpect(jsonPath("$.data.roles[0].headcount").value(2))
                    .andExpect(jsonPath("$.data.roles[0].filledCount").value(0))
                    .andExpect(jsonPath("$.data.leader.nickname").value("팀장"))
                    .andExpect(jsonPath("$.data.isMine").value(false))
                    .andExpect(jsonPath("$.data.myApplication").doesNotExist())
                    .andExpect(jsonPath("$.data.event").doesNotExist())
                    .andExpect(jsonPath("$.data.contactLink").doesNotExist());
        }

        @Test
        @DisplayName("팀장 본인은 isMine=true")
        void mine() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            getAs(leaderToken, "/api/posts/" + post.getId())
                    .andExpect(jsonPath("$.data.isMine").value(true))
                    .andExpect(jsonPath("$.data.myApplication").doesNotExist());
        }

        @Test
        @DisplayName("지원한 사람은 myApplication에 내 지원이 담긴다")
        void myApplication() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            Long applicationId = applyOk(aliceToken, post.getId(), design.getId());

            getAs(aliceToken, "/api/posts/" + post.getId())
                    .andExpect(jsonPath("$.data.isMine").value(false))
                    .andExpect(jsonPath("$.data.myApplication.id").value(applicationId))
                    .andExpect(jsonPath("$.data.myApplication.postRoleId").value(design.getId()))
                    .andExpect(jsonPath("$.data.myApplication.status").value("PENDING"));
            getAs(bobToken, "/api/posts/" + post.getId())
                    .andExpect(jsonPath("$.data.myApplication").doesNotExist());
        }

        @Test
        @DisplayName("공모전이면 행사 정보가 담긴다")
        void event() throws Exception {
            Post post = savePost(leaderId, "공모전 같이 나가실 분", PostCategory.CONTEST, MeetingType.ONLINE, null,
                    LocalDate.now().plusDays(7), 30);
            eventRepository.save(Event.builder().post(post).name("충남 빅데이터 분석 경진대회").host("충청남도")
                    .officialUrl("https://example.com/bigdata").applyDeadline(LocalDate.now().plusDays(7))
                    .build());

            getAs(null, "/api/posts/" + post.getId())
                    .andExpect(jsonPath("$.data.event.name").value("충남 빅데이터 분석 경진대회"))
                    .andExpect(jsonPath("$.data.event.host").value("충청남도"));
        }

        @Test
        @DisplayName("없는 글·숨김 글은 404")
        void notFound() throws Exception {
            getAs(null, "/api/posts/999999").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));

            Post hidden = savePost("숨김 처리된 모집글");
            ReflectionTestUtils.setField(hidden, "hidden", true);
            postRepository.save(hidden);
            getAs(null, "/api/posts/" + hidden.getId()).andExpect(status().isNotFound());
        }
    }

    // ===== POST-04 수동 마감 =====

    @Nested
    @DisplayName("수동 마감 PATCH /api/posts/{postId}/close")
    class Close {

        @Test
        @DisplayName("팀장이 마감하면 글·역할·대기 지원이 마감되고, 대기자에게만 알림")
        void success() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            PostRole dev = saveRole(post, RoleCode.DEV, "백엔드", 1);
            Long aliceApp = applyOk(aliceToken, post.getId(), design.getId());
            Long bobApp = applyOk(bobToken, post.getId(), dev.getId());
            mockMvc.perform(patch("/api/applications/" + bobApp + "/reject")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken))
                    .andExpect(status().isOk());
            notificationRepository.deleteAll();

            close(leaderToken, post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            Post closed = postRepository.findById(post.getId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(RecruitStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();
            assertThat(postRoleRepository.findAllByPostIdOrderByIdAsc(post.getId()))
                    .allMatch(role -> role.getStatus() == RecruitStatus.CLOSED);
            assertThat(applicationRepository.findById(aliceApp).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.CLOSED);
            assertThat(applicationRepository.findById(bobApp).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.REJECTED);

            getAs(aliceToken, "/api/notifications/unread-count").andExpect(jsonPath("$.data.count").value(1));
            getAs(bobToken, "/api/notifications/unread-count").andExpect(jsonPath("$.data.count").value(0));

            // 마감 후 목록 기본값에서 빠짐
            list("").andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("이미 마감된 글은 409 POST_ALREADY_CLOSED")
        void alreadyClosed() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            close(leaderToken, post.getId()).andExpect(status().isOk());
            close(leaderToken, post.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_ALREADY_CLOSED"));
        }

        @Test
        @DisplayName("팀장이 아니면 403, 비회원은 401, 없는 글은 404")
        void forbidden() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            close(aliceToken, post.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            close(null, post.getId()).andExpect(status().isUnauthorized());
            close(leaderToken, 999999L).andExpect(status().isNotFound());

            assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                    .isEqualTo(RecruitStatus.OPEN);
        }
    }
}
