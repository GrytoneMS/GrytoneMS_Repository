package com.grytonems.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
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
import java.time.temporal.ChronoUnit;
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
import com.grytonems.backend.domain.post.EventRepository;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.report.ReportAction;
import com.grytonems.backend.domain.report.ReportRepository;
import com.grytonems.backend.domain.report.ReportStatus;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;

/**
 * 이용 정지 API 테스트 (ADMN-04 정지·해제·기간 종료, 로그인·내 정보 안내, 신고 처리 SUSPEND 연결)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SuspensionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SuspensionScheduler scheduler;
    @Autowired UserRepository userRepository;
    @Autowired UserSuspensionRepository suspensionRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ReportRepository reportRepository;

    School school;
    String adminToken;
    Long adminId;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    String bobToken;
    String carolToken;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        adminToken = signup("admin@example.com", "관리자", false);
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        userRepository.save(admin);
        adminId = admin.getId();

        leaderToken = signup("leader@example.com", "팀장", true);
        leaderId = idOf("leader@example.com");
        aliceToken = signup("alice@example.com", "앨리스", true);
        bobToken = signup("bob@example.com", "밥이", true);
        carolToken = signup("carol@example.com", "캐롤", true);
    }

    @AfterEach
    void cleanUp() {
        suspensionRepository.deleteAll();
        reportRepository.deleteAll();
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

    private Long idOf(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private Post savePost(Long leader, String title) {
        LocalDate deadline = LocalDate.now().plusDays(7);
        return postRepository.save(Post.builder()
                .leaderId(leader)
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

    private Post reload(Post post) {
        return postRepository.findById(post.getId()).orElseThrow();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions suspend(String token, Long userId, Integer days, String reason) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("days", days);
        body.put("reason", reason);
        return as(token, post("/api/admin/users/" + userId + "/suspension")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions lift(String token, Long userId) throws Exception {
        return as(token, delete("/api/admin/users/" + userId + "/suspension"));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "abcd1234"))));
    }

    private ResultActions apply(String token, Long postId, Long roleId) throws Exception {
        return as(token, post("/api/posts/" + postId + "/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("postRoleId", roleId,
                        "message", "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.",
                        "availableTime", "평일 저녁"))));
    }

    private Long applyOk(String token, Long postId, Long roleId) throws Exception {
        String json = apply(token, postId, roleId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private UserStatus statusOf(Long userId) {
        return userRepository.findById(userId).orElseThrow().getStatus();
    }

    // ===== 정지 =====

    @Nested
    @DisplayName("정지 POST /api/admin/users/{userId}/suspension")
    class Suspend {

        @Test
        @DisplayName("7일 정지: 상태 SUSPENDED, 글쓰기는 403 SUSPENDED, 글은 숨김 표시 없이 그대로")
        void sevenDays() throws Exception {
            Post open = savePost(leaderId, "모집 중인 팀장 모집글");

            String json = suspend(adminToken, leaderId, 7, "허위 모집 반복")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(leaderId))
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
                    .andReturn().getResponse().getContentAsString();
            LocalDateTime endsAt = LocalDateTime.parse(objectMapper.readTree(json).at("/data/endsAt").asText());
            assertThat(endsAt).isCloseTo(LocalDateTime.now().plusDays(7), within(1, ChronoUnit.MINUTES));

            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.SUSPENDED);
            assertThat(reload(open).isHidden()).isFalse();

            as(leaderToken, post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SUSPENDED"));
        }

        @Test
        @DisplayName("팀장이 정지 중인 글은 목록·상세·지원·스크랩에서 안 보이고, 대기 지원은 마감 + 지원자에게 알림")
        void postsDisappear() throws Exception {
            Post leaderPost = savePost(leaderId, "모집 중인 팀장 모집글");
            PostRole role = postRoleRepository.save(PostRole.builder()
                    .post(leaderPost).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(3).build());
            Post alicePost = savePost(idOf("alice@example.com"), "앨리스가 올린 모집글");
            Long pending = applyOk(bobToken, leaderPost.getId(), role.getId());
            as(aliceToken, put("/api/posts/" + leaderPost.getId() + "/scrap")).andExpect(status().isOk());
            notificationRepository.deleteAll();

            suspend(adminToken, leaderId, 7, "허위 모집 반복").andExpect(status().isOk());

            as(aliceToken, get("/api/posts"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(alicePost.getId().intValue())));
            as(null, get("/api/posts/" + leaderPost.getId())).andExpect(status().isNotFound());
            apply(carolToken, leaderPost.getId(), role.getId()).andExpect(status().isNotFound());
            as(aliceToken, put("/api/posts/" + leaderPost.getId() + "/scrap")).andExpect(status().isNotFound());
            as(aliceToken, get("/api/users/me/scraps")).andExpect(jsonPath("$.data.items", hasSize(0)));

            assertThat(applicationRepository.findById(pending).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.CLOSED);
            as(bobToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_CLOSED"))
                    .andExpect(jsonPath("$.data.items[0].targetId").doesNotExist());
        }

        @Test
        @DisplayName("정지 중에도 로그인은 되고, 로그인·내 정보 응답에 사유와 기간이 담긴다")
        void loginShowsSuspension() throws Exception {
            suspend(adminToken, leaderId, 30, "욕설 신고 누적").andExpect(status().isOk());

            login("leader@example.com")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.data.user.suspension.reason").value("욕설 신고 누적"))
                    .andExpect(jsonPath("$.data.user.suspension.endsAt").isNotEmpty());
            as(leaderToken, get("/api/users/me"))
                    .andExpect(jsonPath("$.data.suspension.reason").value("욕설 신고 누적"));
            as(aliceToken, get("/api/users/me")).andExpect(jsonPath("$.data.suspension").doesNotExist());
        }

        @Test
        @DisplayName("영구 정지는 endsAt이 null")
        void permanent() throws Exception {
            suspend(adminToken, leaderId, null, "불법 홍보")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.endsAt").doesNotExist());
            as(leaderToken, get("/api/users/me"))
                    .andExpect(jsonPath("$.data.suspension.reason").value("불법 홍보"))
                    .andExpect(jsonPath("$.data.suspension.endsAt").doesNotExist());
        }

        @Test
        @DisplayName("기간은 7·30·영구만, 사유 필수, 관리자 대상 403, 중복 409, 없는 사용자 404, 관리자만 호출")
        void errors() throws Exception {
            suspend(adminToken, leaderId, 10, "사유").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            suspend(adminToken, leaderId, 7, " ").andExpect(status().isBadRequest());
            suspend(adminToken, adminId, 7, "사유").andExpect(status().isForbidden());
            suspend(adminToken, 999999L, 7, "사유").andExpect(status().isNotFound());
            suspend(aliceToken, leaderId, 7, "사유").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.VERIFIED);

            suspend(adminToken, leaderId, 7, "사유").andExpect(status().isOk());
            suspend(adminToken, leaderId, 30, "사유").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_SUSPENDED"));
        }
    }

    // ===== 해제 =====

    @Nested
    @DisplayName("해제 DELETE /api/admin/users/{userId}/suspension, 기간 종료")
    class Lift {

        @Test
        @DisplayName("관리자가 풀면 이전 상태로, 글은 다시 보이고, 정지의 끝난 시각이 기록된다")
        void liftByAdmin() throws Exception {
            Post open = savePost(leaderId, "모집 중인 팀장 모집글");
            suspend(adminToken, leaderId, null, "사유").andExpect(status().isOk());

            lift(adminToken, leaderId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                    .andExpect(jsonPath("$.data.endsAt").doesNotExist());

            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.VERIFIED);
            as(null, get("/api/posts/" + open.getId())).andExpect(status().isOk());
            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(1)));
            assertThat(suspensionRepository.findAll().get(0).getEndsAt()).isNotNull();
            as(leaderToken, get("/api/users/me")).andExpect(jsonPath("$.data.suspension").doesNotExist());

            lift(adminToken, leaderId).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NOT_SUSPENDED"));
        }

        @Test
        @DisplayName("학교 인증 전에 정지된 사람은 풀리면 미인증으로")
        void unverifiedStaysUnverified() throws Exception {
            signup("new@example.com", "새회원", false);
            Long newId = idOf("new@example.com");
            suspend(adminToken, newId, 7, "사유").andExpect(status().isOk());

            lift(adminToken, newId).andExpect(jsonPath("$.data.status").value("UNVERIFIED"));
        }

        @Test
        @DisplayName("기간이 끝난 정지만 스케줄러가 푼다 (영구 정지는 그대로)")
        void scheduler() throws Exception {
            Long aliceId = idOf("alice@example.com");
            suspend(adminToken, leaderId, 7, "사유").andExpect(status().isOk());
            suspend(adminToken, aliceId, null, "사유").andExpect(status().isOk());
            Post open = savePost(leaderId, "정지 기간이 끝날 팀장 글");
            as(null, get("/api/posts/" + open.getId())).andExpect(status().isNotFound());

            assertThat(scheduler.liftExpired(LocalDateTime.now())).isZero();
            assertThat(scheduler.liftExpired(LocalDateTime.now().plusDays(8))).isEqualTo(1);

            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.VERIFIED);
            assertThat(statusOf(aliceId)).isEqualTo(UserStatus.SUSPENDED);
            as(null, get("/api/posts/" + open.getId())).andExpect(status().isOk());
            assertThat(scheduler.liftExpired(LocalDateTime.now().plusDays(8))).isZero();
        }
    }

    // ===== 신고 처리 SUSPEND =====

    @Nested
    @DisplayName("신고 처리 SUSPEND")
    class FromReport {

        private Long reportOk(String token, String type, Long targetId) throws Exception {
            String json = as(token, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("targetType", type, "targetId", targetId, "reasonCode", "FAKE"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return objectMapper.readTree(json).at("/data/reportId").asLong();
        }

        private ResultActions resolve(Long reportId, Map<String, Object> body) throws Exception {
            return as(adminToken, patch("/api/admin/reports/" + reportId + "/resolve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)));
        }

        @Test
        @DisplayName("모집글 신고를 SUSPEND로 처리하면 팀장이 정지되고, 근거 신고가 기록되고, 신고는 처리완료")
        void suspendPostAuthor() throws Exception {
            Post post = savePost(leaderId, "허위로 의심되는 모집글");
            Long first = reportOk(aliceToken, "POST", post.getId());
            reportOk(bobToken, "POST", post.getId());

            resolve(first, Map.of("action", "SUSPEND", "suspendDays", 30, "suspendReason", "허위 모집"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("SUSPEND"))
                    .andExpect(jsonPath("$.data.resolvedCount").value(2));

            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.SUSPENDED);
            as(null, get("/api/posts/" + post.getId())).andExpect(status().isNotFound());
            List<UserSuspension> suspensions = suspensionRepository.findAll();
            assertThat(suspensions).hasSize(1);
            assertThat(suspensions.get(0).getReport().getId()).isEqualTo(first);
            assertThat(reportRepository.findAll()).allSatisfy(r -> {
                assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
                assertThat(r.getAction()).isEqualTo(ReportAction.SUSPEND);
            });
        }

        @Test
        @DisplayName("사용자 신고도 SUSPEND 가능, 사유가 없으면 400이고 신고는 그대로")
        void suspendUserAndValidation() throws Exception {
            Long reportId = reportOk(aliceToken, "USER", leaderId);

            resolve(reportId, Map.of("action", "SUSPEND", "suspendDays", 7))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.VERIFIED);
            assertThat(reportRepository.findById(reportId).orElseThrow().getStatus()).isEqualTo(ReportStatus.RECEIVED);

            resolve(reportId, Map.of("action", "SUSPEND", "suspendDays", 7, "suspendReason", "욕설"))
                    .andExpect(status().isOk());
            assertThat(statusOf(leaderId)).isEqualTo(UserStatus.SUSPENDED);
            as(carolToken, get("/api/users/me")).andExpect(jsonPath("$.data.status").value("VERIFIED"));
        }
    }
}
