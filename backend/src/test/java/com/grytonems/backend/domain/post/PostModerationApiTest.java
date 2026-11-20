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
import com.grytonems.backend.domain.user.UserRole;

/**
 * 모집글 숨김·복구 통합 테스트 (ADMN-02 숨김 조치, 11/11 팀 결정 숨김 정책)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostModerationApiTest {

    private static final String TITLE = "충남 청년 아이디어 공모전 팀원 구해요";
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

    School school;
    String adminToken;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        adminToken = signup("admin@example.com", "관리자");
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        userRepository.save(admin);

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

    private String signupVerified(String email, String nickname) throws Exception {
        String token = signup(email, nickname);
        User user = userRepository.findByEmail(email).orElseThrow();
        user.verify(school, email.replace("@example.com", "@dankook.ac.kr"), LocalDateTime.now());
        userRepository.save(user);
        return token;
    }

    private Post savePost() {
        return postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title(TITLE)
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(LocalDate.now().plusDays(10))
                .periodEnd(LocalDate.now().plusDays(40))
                .deadline(LocalDate.now().plusDays(7))
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
    }

    private PostRole saveRole(Post post, RoleCode code, String name) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(name).headcount(2).build());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions hide(String token, Long postId) throws Exception {
        return as(token, patch("/api/admin/posts/" + postId + "/hide"));
    }

    private ResultActions restore(String token, Long postId) throws Exception {
        return as(token, patch("/api/admin/posts/" + postId + "/restore"));
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

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    // ===== 숨김 =====

    @Nested
    @DisplayName("숨김 PATCH /api/admin/posts/{postId}/hide")
    class Hide {

        @Test
        @DisplayName("숨기면 목록·상세에서 사라지고, 팀장 본인에게도 404")
        void hiddenEverywhere() throws Exception {
            Post post = savePost();

            hide(adminToken, post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(postRepository.findById(post.getId()).orElseThrow().isHidden()).isTrue();
            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(0)));
            as(null, get("/api/posts/" + post.getId())).andExpect(status().isNotFound());
            as(leaderToken, get("/api/posts/" + post.getId())).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("모집 상태는 그대로 두고, 대기 지원만 마감. 수락·거절된 지원은 그대로")
        void closesPendingOnly() throws Exception {
            Post post = savePost();
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            PostRole dev = saveRole(post, RoleCode.DEV, "백엔드");
            Long alicePending = applyOk(aliceToken, post.getId(), design.getId());
            Long bobAccepted = applyOk(bobToken, post.getId(), dev.getId());
            as(leaderToken, patch("/api/applications/" + bobAccepted + "/accept")).andExpect(status().isOk());

            hide(adminToken, post.getId()).andExpect(status().isOk());

            assertThat(statusOf(alicePending)).isEqualTo(ApplicationStatus.CLOSED);
            assertThat(statusOf(bobAccepted)).isEqualTo(ApplicationStatus.ACCEPTED);
            assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                    .isEqualTo(RecruitStatus.OPEN);
        }

        @Test
        @DisplayName("팀장에게 이동 대상 없는 숨김 알림, 대기 지원자에게 마감 알림")
        void notifications() throws Exception {
            Post post = savePost();
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            applyOk(aliceToken, post.getId(), design.getId());
            notificationRepository.deleteAll();

            hide(adminToken, post.getId()).andExpect(status().isOk());

            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_HIDDEN"))
                    .andExpect(jsonPath("$.data.items[0].targetType").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].targetId").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].message")
                            .value("'" + TITLE + "' 모집글이 운영 정책에 따라 숨김 처리됐어요"));
            as(aliceToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_CLOSED"))
                    .andExpect(jsonPath("$.data.items[0].targetId").doesNotExist());
            as(bobToken, get("/api/notifications")).andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("숨김 글에는 지원할 수 없다 (404)")
        void cannotApply() throws Exception {
            Post post = savePost();
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            hide(adminToken, post.getId()).andExpect(status().isOk());

            as(aliceToken, post("/api/posts/" + post.getId() + "/applications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("postRoleId", design.getId(), "message", MESSAGE,
                                            "availableTime", "평일 저녁"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("숨김 글은 모집 중인 글 3개 제한에서 빠진다")
        void notCountedInLimit() throws Exception {
            List<Post> posts = List.of(savePost(), savePost(), savePost());
            hide(adminToken, posts.get(0).getId()).andExpect(status().isOk());

            Map<String, Object> body = Map.of(
                    "title", TITLE, "category", "PROJECT",
                    "description", "아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.",
                    "periodStart", LocalDate.now().plusDays(10).toString(),
                    "periodEnd", LocalDate.now().plusDays(40).toString(),
                    "deadline", LocalDate.now().plusDays(7).toString(),
                    "meetingType", "ONLINE",
                    "roles", List.of(Map.of("roleCode", "DESIGN", "roleName", "UI 디자인", "headcount", 1)),
                    "contactLink", "https://open.kakao.com/o/test");
            as(leaderToken, post("/api/posts").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("이미 숨긴 글은 409, 없는 글은 404")
        void errors() throws Exception {
            Post post = savePost();
            hide(adminToken, post.getId()).andExpect(status().isOk());
            hide(adminToken, post.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_ALREADY_HIDDEN"));
            hide(adminToken, 999999L).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("관리자가 아니면 403, 비회원은 401")
        void adminOnly() throws Exception {
            Post post = savePost();
            hide(leaderToken, post.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            hide(null, post.getId()).andExpect(status().isUnauthorized());
            assertThat(postRepository.findById(post.getId()).orElseThrow().isHidden()).isFalse();
        }
    }

    // ===== 복구 =====

    @Nested
    @DisplayName("복구 PATCH /api/admin/posts/{postId}/restore")
    class Restore {

        @Test
        @DisplayName("복구하면 다시 보이고, 팀장에게 글로 이동하는 복구 알림")
        void success() throws Exception {
            Post post = savePost();
            hide(adminToken, post.getId()).andExpect(status().isOk());
            notificationRepository.deleteAll();

            restore(adminToken, post.getId()).andExpect(status().isOk());

            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(1)));
            as(leaderToken, get("/api/posts/" + post.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isMine").value(true));
            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_RESTORED"))
                    .andExpect(jsonPath("$.data.items[0].targetType").value("POST"))
                    .andExpect(jsonPath("$.data.items[0].targetId").value(post.getId()));
        }

        @Test
        @DisplayName("숨김 전에 마감된 글은 복구해도 마감 상태, 숨김 때 마감된 지원은 되돌리지 않음")
        void keepsPreviousState() throws Exception {
            Post post = savePost();
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인");
            Long alicePending = applyOk(aliceToken, post.getId(), design.getId());
            hide(adminToken, post.getId()).andExpect(status().isOk());
            restore(adminToken, post.getId()).andExpect(status().isOk());
            assertThat(statusOf(alicePending)).isEqualTo(ApplicationStatus.CLOSED);

            Post closed = savePost();
            as(leaderToken, patch("/api/posts/" + closed.getId() + "/close")).andExpect(status().isOk());
            hide(adminToken, closed.getId()).andExpect(status().isOk());
            restore(adminToken, closed.getId()).andExpect(status().isOk());
            assertThat(postRepository.findById(closed.getId()).orElseThrow().getStatus())
                    .isEqualTo(RecruitStatus.CLOSED);
        }

        @Test
        @DisplayName("숨김 글이 아니면 409 POST_NOT_HIDDEN, 관리자가 아니면 403")
        void errors() throws Exception {
            Post post = savePost();
            restore(adminToken, post.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_NOT_HIDDEN"));
            hide(adminToken, post.getId()).andExpect(status().isOk());
            restore(leaderToken, post.getId()).andExpect(status().isForbidden());
        }
    }
}
