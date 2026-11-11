package com.grytonems.backend.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * 마감일 기준 처리 테스트 (POST-06 글 자동 마감, APPL-08 일괄 처리, NOTI-01 마감 D-1 알림).
 * 정해진 시각 실행은 테스트에서 꺼져 있어서, 스케줄러 메서드에 "오늘"을 넘겨 직접 호출합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostDeadlineTest {

    private static final String MESSAGE = "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostDeadlineScheduler scheduler;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;

    LocalDate today = LocalDate.now();
    School school;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signupVerified("alice@example.com", "앨리스");
        bobToken = signupVerified("bob@example.com", "밥이");
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

    private String signupVerified(String email, String nickname) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", "abcd1234", "nickname", nickname,
                "agreeTerms", true, "agreePrivacy", true);
        String json = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        User user = userRepository.findByEmail(email).orElseThrow();
        user.verify(school, email.replace("@example.com", "@dankook.ac.kr"), LocalDateTime.now());
        userRepository.save(user);
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private Post savePost(String title, LocalDate deadline) {
        return postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title(title)
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
    }

    private PostRole saveRole(Post post, RoleCode code, String name) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(name).headcount(2).build());
    }

    /** 지원은 마감일 전에만 되므로, 지원을 받은 뒤 마감일을 과거로 옮깁니다 */
    private void moveDeadline(Post post, LocalDate deadline) {
        Post loaded = postRepository.findById(post.getId()).orElseThrow();
        ReflectionTestUtils.setField(loaded, "deadline", deadline);
        postRepository.save(loaded);
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Long applyOk(String token, Long postId, Long roleId) throws Exception {
        String json = as(token, post("/api/posts/" + postId + "/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("postRoleId", roleId, "message", MESSAGE, "availableTime", "평일 저녁"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private Post reload(Post post) {
        return postRepository.findById(post.getId()).orElseThrow();
    }

    // ===== POST-06 자동 마감 =====

    @Nested
    @DisplayName("마감일 지난 글 자동 마감 (POST-06)")
    class AutoClose {

        @Test
        @DisplayName("글·역할을 마감하고, 대기 지원만 마감하고 대기자에게 알림")
        void closesExpired() throws Exception {
            Post post = savePost("마감일 지난 모집글", today.plusDays(3));
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            PostRole dev = saveRole(post, RoleCode.DEV, "백엔드");
            Long alicePending = applyOk(aliceToken, post.getId(), design.getId());
            Long bobAccepted = applyOk(bobToken, post.getId(), dev.getId());
            as(leaderToken, patch("/api/applications/" + bobAccepted + "/accept")).andExpect(status().isOk());
            moveDeadline(post, today.minusDays(1));
            notificationRepository.deleteAll();

            assertThat(scheduler.closeExpiredPosts(today)).isEqualTo(1);

            Post closed = reload(post);
            assertThat(closed.getStatus()).isEqualTo(RecruitStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();
            assertThat(postRoleRepository.findAllByPostIdOrderByIdAsc(post.getId()))
                    .allMatch(role -> role.getStatus() == RecruitStatus.CLOSED);
            assertThat(applicationRepository.findById(alicePending).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.CLOSED);
            assertThat(applicationRepository.findById(bobAccepted).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.ACCEPTED);

            as(aliceToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_CLOSED"))
                    .andExpect(jsonPath("$.data.items[0].message").value("'마감일 지난 모집글' 모집이 마감되었어요"));
            as(bobToken, get("/api/notifications")).andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("오늘 마감인 글(23:59:59까지)과 마감일이 남은 글은 그대로")
        void keepsTodayAndFuture() {
            Post todayPost = savePost("오늘 마감인 모집글", today);
            Post future = savePost("내일 마감인 모집글", today.plusDays(1));

            assertThat(scheduler.closeExpiredPosts(today)).isZero();
            assertThat(reload(todayPost).getStatus()).isEqualTo(RecruitStatus.OPEN);
            assertThat(reload(future).getStatus()).isEqualTo(RecruitStatus.OPEN);
        }

        @Test
        @DisplayName("여러 글을 한 번에 마감하고, 다시 돌려도 중복 처리·중복 알림 없음")
        void idempotent() throws Exception {
            Post first = savePost("첫 번째 지난 글", today.plusDays(3));
            PostRole design = saveRole(first, RoleCode.DESIGN, "UI 디자인");
            applyOk(aliceToken, first.getId(), design.getId());
            moveDeadline(first, today.minusDays(1));
            savePost("두 번째 지난 글", today.minusDays(5));
            notificationRepository.deleteAll();

            assertThat(scheduler.closeExpiredPosts(today)).isEqualTo(2);
            assertThat(scheduler.closeExpiredPosts(today)).isZero();
            assertThat(notificationRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 마감된 글·삭제된 글은 건드리지 않고, 숨김 글은 상태만 마감")
        void skipsClosedAndDeleted() {
            Post closed = savePost("수동으로 마감한 글", today.minusDays(1));
            closed.close();
            postRepository.save(closed);
            LocalDateTime closedAt = reload(closed).getClosedAt();

            Post deleted = savePost("삭제된 모집글이에요", today.minusDays(1));
            ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
            postRepository.save(deleted);

            Post hidden = savePost("숨김 처리된 모집글", today.minusDays(1));
            hidden.hide();
            postRepository.save(hidden);

            assertThat(scheduler.closeExpiredPosts(today)).isEqualTo(1);
            assertThat(reload(closed).getClosedAt()).isEqualTo(closedAt);
            assertThat(reload(deleted).getStatus()).isEqualTo(RecruitStatus.OPEN);
            assertThat(reload(hidden).getStatus()).isEqualTo(RecruitStatus.CLOSED);
        }

        @Test
        @DisplayName("마감 후 목록 기본값(모집중만)에서 빠지고, 지원은 409 ROLE_CLOSED")
        void hiddenFromOpenList() throws Exception {
            Post post = savePost("마감일 지난 모집글", today.minusDays(1));
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            Post open = savePost("아직 모집 중인 글", today.plusDays(3));

            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(2)));
            scheduler.closeExpiredPosts(today);

            as(null, get("/api/posts"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].id").value(open.getId()));
            as(null, get("/api/posts/" + post.getId())).andExpect(jsonPath("$.data.status").value("CLOSED"));
            as(aliceToken, post("/api/posts/" + post.getId() + "/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("postRoleId", design.getId(),
                                    "message", MESSAGE, "availableTime", "평일 저녁"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROLE_CLOSED"));
        }
    }

    // ===== NOTI-01 마감 D-1 =====

    @Nested
    @DisplayName("마감 D-1 알림 (팀장)")
    class DeadlineSoon {

        @Test
        @DisplayName("내일 마감인 모집중 글의 팀장에게만, 글로 이동하는 알림")
        void notifiesTomorrowOnly() throws Exception {
            Post tomorrow = savePost("내일 마감인 모집글", today.plusDays(1));
            savePost("오늘 마감인 모집글", today);
            savePost("모레 마감인 모집글", today.plusDays(2));

            assertThat(scheduler.notifyDeadlineTomorrowFor(today)).isEqualTo(1);

            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("DEADLINE_SOON"))
                    .andExpect(jsonPath("$.data.items[0].targetType").value("POST"))
                    .andExpect(jsonPath("$.data.items[0].targetId").value(tomorrow.getId()))
                    .andExpect(jsonPath("$.data.items[0].message").value("'내일 마감인 모집글' 마감이 내일이에요"));
        }

        @Test
        @DisplayName("마감된 글·숨김 글은 알림 없음")
        void skipsClosedAndHidden() {
            Post closed = savePost("이미 마감한 모집글", today.plusDays(1));
            closed.close();
            postRepository.save(closed);
            Post hidden = savePost("숨김 처리된 모집글", today.plusDays(1));
            hidden.hide();
            postRepository.save(hidden);

            assertThat(scheduler.notifyDeadlineTomorrowFor(today)).isZero();
            assertThat(notificationRepository.count()).isZero();
        }
    }
}
