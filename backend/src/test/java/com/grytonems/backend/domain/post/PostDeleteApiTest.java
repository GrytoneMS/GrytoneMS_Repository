package com.grytonems.backend.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.grytonems.backend.domain.report.ReportAction;
import com.grytonems.backend.domain.report.ReportRepository;
import com.grytonems.backend.domain.scrap.ScrapRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserRole;

/**
 * 모집글 삭제 테스트: 팀장 직접 삭제 (POST-03), 관리자 신고 처리 삭제 (ADMN-02 DELETE)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostDeleteApiTest {

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
    @Autowired ScrapRepository scrapRepository;
    @Autowired ReportRepository reportRepository;

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
        adminToken = signup("admin@example.com", "관리자", false);
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        userRepository.save(admin);
        leaderToken = signup("leader@example.com", "팀장", true);
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signup("alice@example.com", "앨리스", true);
        bobToken = signup("bob@example.com", "밥이", true);
    }

    @AfterEach
    void cleanUp() {
        reportRepository.deleteAll();
        scrapRepository.deleteAll();
        notificationRepository.deleteAll();
        applicationRepository.deleteAll();
        eventRepository.deleteAll();
        postRoleRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    // ===== helpers =====

    private String signup(String email, String nickname, boolean verified) throws Exception {
        Map<String, Object> body = Map.of("email", email, "password", "abcd1234", "nickname", nickname,
                "agreeTerms", true, "agreePrivacy", true);
        String json = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        if (verified) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.verify(school, email.replace("@example.com", "@dankook.ac.kr"), LocalDateTime.now());
            userRepository.save(user);
        }
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private Post savePost(String title) {
        LocalDate deadline = LocalDate.now().plusDays(7);
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

    private PostRole saveRole(Post post) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(3).build());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions deletePost(String token, Long postId) throws Exception {
        return as(token, delete("/api/posts/" + postId));
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

    // ===== POST-03 팀장 직접 삭제 =====

    @Nested
    @DisplayName("팀장 삭제 DELETE /api/posts/{postId}")
    class ByLeader {

        @Test
        @DisplayName("지원자가 없으면 삭제 표시, 상세는 POST_DELETED, 목록·내 모집글·내 스크랩에서 빠진다")
        void success() throws Exception {
            Post post = savePost("삭제할 모집글이에요");
            as(aliceToken, put("/api/posts/" + post.getId() + "/scrap")).andExpect(status().isOk());

            deletePost(leaderToken, post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(reload(post).getDeletedAt()).isNotNull();
            as(null, get("/api/posts/" + post.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("POST_DELETED"));
            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(0)));
            as(leaderToken, get("/api/users/me/posts")).andExpect(jsonPath("$.data.items", hasSize(0)));
            as(aliceToken, get("/api/users/me/scraps")).andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("대기·수락·거절 지원자가 있으면 409 POST_HAS_APPLICANTS, 취소만 있으면 삭제 가능")
        void applicants() throws Exception {
            Post withPending = savePost("대기 지원자가 있는 글");
            applyOk(aliceToken, withPending.getId(), saveRole(withPending).getId());
            deletePost(leaderToken, withPending.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("POST_HAS_APPLICANTS"));

            Post withRejected = savePost("거절한 지원자가 있는 글");
            Long rejected = applyOk(aliceToken, withRejected.getId(), saveRole(withRejected).getId());
            as(leaderToken, patch("/api/applications/" + rejected + "/reject")).andExpect(status().isOk());
            deletePost(leaderToken, withRejected.getId()).andExpect(status().isConflict());

            Post withCanceled = savePost("취소한 지원자만 있는 글");
            Long canceled = applyOk(aliceToken, withCanceled.getId(), saveRole(withCanceled).getId());
            as(aliceToken, patch("/api/applications/" + canceled + "/cancel")).andExpect(status().isOk());
            deletePost(leaderToken, withCanceled.getId()).andExpect(status().isOk());

            assertThat(reload(withPending).getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("팀장이 아니면 403, 이미 삭제·숨김·없는 글은 404, 비회원은 401")
        void errors() throws Exception {
            Post post = savePost("삭제할 모집글이에요");
            deletePost(aliceToken, post.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            deletePost(null, post.getId()).andExpect(status().isUnauthorized());
            deletePost(leaderToken, 999999L).andExpect(status().isNotFound());

            deletePost(leaderToken, post.getId()).andExpect(status().isOk());
            deletePost(leaderToken, post.getId()).andExpect(status().isNotFound());

            Post hidden = savePost("숨김 처리된 모집글");
            hidden.hide();
            postRepository.save(hidden);
            deletePost(leaderToken, hidden.getId()).andExpect(status().isNotFound());
        }
    }

    // ===== ADMN-02 신고 처리 삭제 =====

    @Nested
    @DisplayName("관리자 신고 처리 DELETE")
    class ByAdmin {

        private Long reportOk(String token, Long postId) throws Exception {
            String json = as(token, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("targetType", "POST", "targetId", postId, "reasonCode", "FAKE"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(json).at("/data/reportId").asLong();
        }

        @Test
        @DisplayName("지원자가 있어도 삭제, 대기 지원은 마감 + 지원자 알림, 팀장에게 POST_DELETED, 수락은 그대로")
        void deletesWithApplicants() throws Exception {
            Post post = savePost("허위로 의심되는 모집글");
            PostRole role = saveRole(post);
            Long pending = applyOk(aliceToken, post.getId(), role.getId());
            Long accepted = applyOk(bobToken, post.getId(), role.getId());
            as(leaderToken, patch("/api/applications/" + accepted + "/accept")).andExpect(status().isOk());
            Long reportId = reportOk(aliceToken, post.getId());
            notificationRepository.deleteAll();

            as(adminToken, patch("/api/admin/reports/" + reportId + "/resolve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("action", "DELETE"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("DELETE"));

            assertThat(reload(post).getDeletedAt()).isNotNull();
            assertThat(applicationRepository.findById(pending).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.CLOSED);
            assertThat(applicationRepository.findById(accepted).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.ACCEPTED);
            assertThat(reportRepository.findById(reportId).orElseThrow().getAction()).isEqualTo(ReportAction.DELETE);

            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_DELETED"))
                    .andExpect(jsonPath("$.data.items[0].targetId").doesNotExist());
            as(aliceToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items[*].type",
                            org.hamcrest.Matchers.hasItems("POST_CLOSED", "REPORT_RESOLVED")));
            as(null, get("/api/posts/" + post.getId())).andExpect(jsonPath("$.code").value("POST_DELETED"));
        }
    }
}
