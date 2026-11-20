package com.grytonems.backend.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.post.EventRepository;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserRole;

/**
 * 신고 API 테스트 (SAFE-01 신고, SAFE-02 신고 3건 자동 숨김, ADMN-02 관리자 신고 처리)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiTest {

    private static final String TITLE = "디자이너 구해요 프로젝트";
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
    @Autowired ReportRepository reportRepository;

    School school;
    String adminToken;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    Long aliceId;
    String bobToken;
    String carolToken;
    String daveToken;

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
        leaderId = idOf("leader@example.com");
        aliceToken = signup("alice@example.com", "앨리스", true);
        aliceId = idOf("alice@example.com");
        bobToken = signup("bob@example.com", "밥이", true);
        carolToken = signup("carol@example.com", "캐롤", true);
        daveToken = signup("dave@example.com", "데이브", true);
    }

    @AfterEach
    void cleanUp() {
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

    private Post savePost() {
        LocalDate deadline = LocalDate.now().plusDays(7);
        return postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title(TITLE)
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions report(String token, String targetType, Object targetId, String reason, String detail)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("reasonCode", reason);
        body.put("detail", detail);
        return as(token, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Long reportOk(String token, String targetType, Long targetId) throws Exception {
        String json = report(token, targetType, targetId, "FAKE", null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/reportId").asLong();
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

    private ResultActions resolve(String token, Long reportId, String action) throws Exception {
        return as(token, patch("/api/admin/reports/" + reportId + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", action))));
    }

    private boolean isHidden(Post post) {
        return postRepository.findById(post.getId()).orElseThrow().isHidden();
    }

    // ===== SAFE-01 신고 =====

    @Nested
    @DisplayName("신고 POST /api/reports")
    class Create {

        @Test
        @DisplayName("모집글·사용자 신고는 201 접수, 상세 내용은 앞뒤 공백 제거")
        void success() throws Exception {
            Post post = savePost();

            report(aliceToken, "POST", post.getId(), "SPAM", "  카톡방 홍보만 해요  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reportId").isNumber())
                    .andExpect(jsonPath("$.data.status").value("RECEIVED"));
            report(aliceToken, "USER", leaderId, "ABUSE", null).andExpect(status().isCreated());

            List<Report> reports = reportRepository.findAll();
            assertThat(reports).hasSize(2);
            Report postReport = reports.stream().filter(r -> r.getTargetType() == ReportTargetType.POST)
                    .findFirst().orElseThrow();
            assertThat(postReport.getReasonCode()).isEqualTo(ReportReason.SPAM);
            assertThat(postReport.getDetail()).isEqualTo("카톡방 홍보만 해요");
            assertThat(postReport.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        }

        @Test
        @DisplayName("같은 대상을 또 신고하면 409 ALREADY_REPORTED, 다른 사람은 가능")
        void duplicate() throws Exception {
            Post post = savePost();
            reportOk(aliceToken, "POST", post.getId());
            report(aliceToken, "POST", post.getId(), "ETC", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
            reportOk(bobToken, "POST", post.getId());
        }

        @Test
        @DisplayName("내 글·나 자신·내 지원은 400 CANNOT_REPORT_SELF")
        void self() throws Exception {
            Post post = savePost();
            PostRole role = postRoleRepository.save(PostRole.builder()
                    .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(2).build());
            Long applicationId = applyOk(aliceToken, post.getId(), role.getId());

            report(leaderToken, "POST", post.getId(), "FAKE", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_REPORT_SELF"));
            report(aliceToken, "USER", aliceId, "ABUSE", null).andExpect(status().isBadRequest());
            report(aliceToken, "APPLICATION", applicationId, "ABUSE", null).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("지원 메시지는 그 글의 팀장만 신고 가능 (그 외 403)")
        void applicationLeaderOnly() throws Exception {
            Post post = savePost();
            PostRole role = postRoleRepository.save(PostRole.builder()
                    .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(2).build());
            Long applicationId = applyOk(aliceToken, post.getId(), role.getId());

            report(bobToken, "APPLICATION", applicationId, "ABUSE", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            report(leaderToken, "APPLICATION", applicationId, "ABUSE", "욕설이 있어요")
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("없는 대상·숨김 글은 404")
        void notFound() throws Exception {
            report(aliceToken, "POST", 999999, "FAKE", null).andExpect(status().isNotFound());
            report(aliceToken, "USER", 999999, "FAKE", null).andExpect(status().isNotFound());
            report(aliceToken, "APPLICATION", 999999, "FAKE", null).andExpect(status().isNotFound());

            Post hidden = savePost();
            hidden.hide();
            postRepository.save(hidden);
            report(aliceToken, "POST", hidden.getId(), "FAKE", null).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("사유 없음·상세 200자 초과·없는 코드값은 400 INVALID_INPUT")
        void validation() throws Exception {
            Post post = savePost();
            report(aliceToken, "POST", post.getId(), null, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reasonCode"));
            report(aliceToken, "POST", post.getId(), "ETC", "가".repeat(201)).andExpect(status().isBadRequest());
            report(aliceToken, "POST", post.getId(), "RUDE", null).andExpect(status().isBadRequest());
            report(aliceToken, "COMMENT", post.getId(), "ETC", null).andExpect(status().isBadRequest());
            assertThat(reportRepository.count()).isZero();
        }

        @Test
        @DisplayName("미인증 회원은 403 UNVERIFIED, 비회원은 401")
        void auth() throws Exception {
            Post post = savePost();
            String unverified = signup("new@example.com", "새회원", false);
            report(unverified, "POST", post.getId(), "FAKE", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
            report(null, "POST", post.getId(), "FAKE", null).andExpect(status().isUnauthorized());
        }
    }

    // ===== SAFE-02 자동 숨김 =====

    @Nested
    @DisplayName("신고 3건 자동 숨김")
    class AutoHide {

        @Test
        @DisplayName("서로 다른 3명이 신고하면 숨김 + 팀장 알림, 2명까지는 그대로")
        void hideOnThird() throws Exception {
            Post post = savePost();
            reportOk(aliceToken, "POST", post.getId());
            reportOk(bobToken, "POST", post.getId());
            assertThat(isHidden(post)).isFalse();

            reportOk(carolToken, "POST", post.getId());
            assertThat(isHidden(post)).isTrue();
            as(null, get("/api/posts/" + post.getId())).andExpect(status().isNotFound());
            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_HIDDEN"));
        }

        @Test
        @DisplayName("사용자 신고는 3건이 쌓여도 숨김과 관계없음")
        void userReportsDoNotHidePosts() throws Exception {
            Post post = savePost();
            reportOk(aliceToken, "USER", leaderId);
            reportOk(bobToken, "USER", leaderId);
            reportOk(carolToken, "USER", leaderId);
            assertThat(isHidden(post)).isFalse();
        }
    }

    // ===== ADMN-02 관리자 신고 처리 =====

    @Nested
    @DisplayName("관리자 신고 목록 GET /api/admin/reports")
    class AdminList {

        @Test
        @DisplayName("최신순, 대상 내용과 같은 대상 미처리 신고 수가 담기고 status로 거른다")
        void list() throws Exception {
            Post post = savePost();
            Long first = reportOk(aliceToken, "POST", post.getId());
            Long second = reportOk(bobToken, "POST", post.getId());
            Long userReport = reportOk(aliceToken, "USER", leaderId);

            as(adminToken, get("/api/admin/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].id",
                            contains(userReport.intValue(), second.intValue(), first.intValue())))
                    .andExpect(jsonPath("$.data.items[0].targetType").value("USER"))
                    .andExpect(jsonPath("$.data.items[0].target.title").value("팀장"))
                    .andExpect(jsonPath("$.data.items[1].target.title").value(TITLE))
                    .andExpect(jsonPath("$.data.items[1].target.authorId").value(leaderId))
                    .andExpect(jsonPath("$.data.items[1].target.hidden").value(false))
                    .andExpect(jsonPath("$.data.items[1].reasonCode").value("FAKE"))
                    .andExpect(jsonPath("$.data.items[1].reporter.nickname").value("밥이"))
                    .andExpect(jsonPath("$.data.items[1].pendingCountForTarget").value(2))
                    .andExpect(jsonPath("$.data.items[1].status").value("RECEIVED"));

            resolve(adminToken, userReport, "NONE").andExpect(status().isOk());
            as(adminToken, get("/api/admin/reports?status=RECEIVED"))
                    .andExpect(jsonPath("$.data.items", hasSize(2)));
            as(adminToken, get("/api/admin/reports?status=RESOLVED"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(userReport.intValue())))
                    .andExpect(jsonPath("$.data.items[0].action").value("NONE"))
                    .andExpect(jsonPath("$.data.items[0].handledAt").isNotEmpty());
        }

        @Test
        @DisplayName("관리자가 아니면 403")
        void adminOnly() throws Exception {
            as(leaderToken, get("/api/admin/reports"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("관리자 신고 처리 PATCH /api/admin/reports/{id}/resolve")
    class Resolve {

        @Test
        @DisplayName("자동 숨김 글을 문제없음 처리하면 복구, 같은 대상 신고 모두 처리완료, 신고자 모두에게 알림")
        void noneRestores() throws Exception {
            Post post = savePost();
            Long first = reportOk(aliceToken, "POST", post.getId());
            reportOk(bobToken, "POST", post.getId());
            reportOk(carolToken, "POST", post.getId());
            assertThat(isHidden(post)).isTrue();
            notificationRepository.deleteAll();

            resolve(adminToken, first, "NONE")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("NONE"))
                    .andExpect(jsonPath("$.data.resolvedCount").value(3));

            assertThat(isHidden(post)).isFalse();
            assertThat(reportRepository.findAll()).allSatisfy(r -> {
                assertThat(r.getStatus()).isEqualTo(ReportStatus.RESOLVED);
                assertThat(r.getAction()).isEqualTo(ReportAction.NONE);
                assertThat(r.getHandledAt()).isNotNull();
            });
            for (String token : List.of(aliceToken, bobToken, carolToken)) {
                as(token, get("/api/notifications"))
                        .andExpect(jsonPath("$.data.items", hasSize(1)))
                        .andExpect(jsonPath("$.data.items[0].type").value("REPORT_RESOLVED"))
                        .andExpect(jsonPath("$.data.items[0].message").value("신고하신 내용이 처리되었어요"))
                        .andExpect(jsonPath("$.data.items[0].targetId").doesNotExist());
            }
            as(leaderToken, get("/api/notifications"))
                    .andExpect(jsonPath("$.data.items[0].type").value("POST_RESTORED"));
        }

        @Test
        @DisplayName("복구 뒤에는 새 신고가 다시 3건 쌓여야 숨김")
        void countsOnlyNewReportsAfterRestore() throws Exception {
            Post post = savePost();
            Long first = reportOk(aliceToken, "POST", post.getId());
            reportOk(bobToken, "POST", post.getId());
            reportOk(carolToken, "POST", post.getId());
            resolve(adminToken, first, "NONE").andExpect(status().isOk());

            reportOk(daveToken, "POST", post.getId());
            assertThat(isHidden(post)).isFalse();
        }

        @Test
        @DisplayName("숨김 조치하면 글이 숨겨지고, 처리된 신고를 또 처리하면 409")
        void hide() throws Exception {
            Post post = savePost();
            Long reportId = reportOk(aliceToken, "POST", post.getId());

            resolve(adminToken, reportId, "HIDE").andExpect(status().isOk());
            assertThat(isHidden(post)).isTrue();

            resolve(adminToken, reportId, "NONE")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("REPORT_ALREADY_RESOLVED"));
        }

        @Test
        @DisplayName("아직 없는 조치·대상에 안 맞는 조치는 400, 없는 신고는 404, 관리자가 아니면 403")
        void errors() throws Exception {
            Post post = savePost();
            Long postReport = reportOk(aliceToken, "POST", post.getId());
            Long userReport = reportOk(aliceToken, "USER", leaderId);

            resolve(adminToken, userReport, "DELETE")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("REPORT_ACTION_NOT_SUPPORTED"));
            resolve(adminToken, userReport, "HIDE").andExpect(status().isBadRequest());
            resolve(adminToken, userReport, "SUSPEND").andExpect(status().isBadRequest());
            resolve(adminToken, 999999L, "NONE").andExpect(status().isNotFound());
            resolve(leaderToken, postReport, "NONE").andExpect(status().isForbidden());

            assertThat(reportRepository.findAll()).allMatch(r -> r.getStatus() == ReportStatus.RECEIVED);
        }
    }
}
