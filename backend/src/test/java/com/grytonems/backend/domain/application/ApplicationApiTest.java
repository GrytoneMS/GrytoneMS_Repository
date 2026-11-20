package com.grytonems.backend.domain.application;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.post.EventRepository;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/**
 * 지원 API 통합 테스트 (APPL-01, 04, 05, 06, 07, 08, 10).
 * 모집글은 저장소로 직접 만들고, 지원·수락·거절은 실제 API를 호출합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationApiTest {

    private static final String MESSAGE = "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.";
    private static final String CONTACT = "https://open.kakao.com/o/test";

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

    private Post savePost(Long leader) {
        return postRepository.save(Post.builder()
                .leaderId(leader)
                .title("충남 청년 아이디어 공모전 팀원 구해요")
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(LocalDate.now().plusDays(10))
                .periodEnd(LocalDate.now().plusDays(40))
                .deadline(LocalDate.now().plusDays(7))
                .meetingType(MeetingType.ONLINE)
                .contactLink(CONTACT)
                .build());
    }

    private PostRole saveRole(Post post, RoleCode code, String name, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(name).headcount(headcount).build());
    }

    private ResultActions apply(String token, Long postId, Long roleId, String message) throws Exception {
        return mockMvc.perform(post("/api/posts/" + postId + "/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("postRoleId", roleId, "message", message, "availableTime", "평일 저녁"))));
    }

    /** 지원하고 지원 id를 돌려줍니다 */
    private Long applyOk(String token, Long postId, Long roleId) throws Exception {
        String json = apply(token, postId, roleId, MESSAGE)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private ResultActions accept(String token, Long applicationId) throws Exception {
        return mockMvc.perform(patch("/api/applications/" + applicationId + "/accept")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions getWith(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    // ===== APPL-01 지원하기 =====

    @Nested
    @DisplayName("지원하기 POST /api/posts/{postId}/applications")
    class Apply {

        @Test
        @DisplayName("성공하면 201과 대기 상태")
        void success() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);

            apply(aliceToken, post.getId(), design.getId(), MESSAGE)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("미인증 회원은 403 UNVERIFIED, 비회원은 401")
        void needsVerified() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            String unverified = signup("new@example.com", "새회원");

            apply(unverified, post.getId(), design.getId(), MESSAGE)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
            mockMvc.perform(post("/api/posts/" + post.getId() + "/applications")
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("내 글에는 지원할 수 없다 (400 CANNOT_APPLY_OWN_POST)")
        void ownPost() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);

            apply(leaderToken, post.getId(), design.getId(), MESSAGE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_APPLY_OWN_POST"));
        }

        @Test
        @DisplayName("같은 글에는 다른 역할로도 다시 지원할 수 없다 (409 ALREADY_APPLIED)")
        void alreadyApplied() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
            applyOk(aliceToken, post.getId(), design.getId());

            apply(aliceToken, post.getId(), dev.getId(), MESSAGE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
        }

        @Test
        @DisplayName("다른 글의 역할 id를 보내면 400 INVALID_INPUT")
        void roleOfAnotherPost() throws Exception {
            Post post = savePost(leaderId);
            saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            Post other = savePost(leaderId);
            PostRole otherRole = saveRole(other, RoleCode.DEV, "백엔드", 1);

            apply(aliceToken, post.getId(), otherRole.getId(), MESSAGE)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        @DisplayName("마감된 글에는 지원할 수 없다 (409 ROLE_CLOSED)")
        void closedPost() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            post.close();
            postRepository.save(post);

            apply(aliceToken, post.getId(), design.getId(), MESSAGE)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROLE_CLOSED"));
        }

        @Test
        @DisplayName("메시지가 20자보다 짧으면 400과 필드 오류, 없는 글이면 404")
        void invalidInputOrNotFound() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);

            apply(aliceToken, post.getId(), design.getId(), "짧은 메시지")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("message"));
            apply(aliceToken, 999_999L, design.getId(), MESSAGE)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // ===== APPL-04 지원자 목록 =====

    @Test
    @DisplayName("지원자 목록: 팀장은 역할별로 묶어서 보고, 다른 사람은 403")
    void applicantList() throws Exception {
        Post post = savePost(leaderId);
        PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
        PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
        applyOk(aliceToken, post.getId(), design.getId());
        applyOk(bobToken, post.getId(), design.getId());

        getWith(leaderToken, "/api/posts/" + post.getId() + "/applications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].postRoleId").value(design.getId()))
                .andExpect(jsonPath("$.data[0].roleName").value("UI 디자인"))
                .andExpect(jsonPath("$.data[0].headcount").value(1))
                .andExpect(jsonPath("$.data[0].filledCount").value(0))
                .andExpect(jsonPath("$.data[0].applications", hasSize(2)))
                .andExpect(jsonPath("$.data[0].applications[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].applications[0].applicant.nickname").value("앨리스"))
                .andExpect(jsonPath("$.data[0].applications[0].applicant.schoolName").value("단국대학교"))
                .andExpect(jsonPath("$.data[0].applications[1].applicant.nickname").value("밥이"))
                .andExpect(jsonPath("$.data[1].postRoleId").value(dev.getId()))
                .andExpect(jsonPath("$.data[1].applications", hasSize(0)));

        getWith(aliceToken, "/api/posts/" + post.getId() + "/applications")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ===== APPL-05 수락, APPL-08 자동 마감 =====

    @Nested
    @DisplayName("수락 PATCH /api/applications/{id}/accept")
    class Accept {

        @Test
        @DisplayName("1명 모집 역할을 수락하면 역할·글이 마감되고 남은 대기 지원도 마감된다")
        void fillsAndClosesEverything() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            Long alice = applyOk(aliceToken, post.getId(), design.getId());
            Long bob = applyOk(bobToken, post.getId(), design.getId());

            accept(leaderToken, alice)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.applicationId").value(alice))
                    .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.data.roleClosed").value(true))
                    .andExpect(jsonPath("$.data.postClosed").value(true));

            assertThat(statusOf(alice)).isEqualTo(ApplicationStatus.ACCEPTED);
            assertThat(applicationRepository.findById(alice).orElseThrow().getDecidedAt()).isNotNull();
            assertThat(statusOf(bob)).isEqualTo(ApplicationStatus.CLOSED);
            PostRole role = postRoleRepository.findById(design.getId()).orElseThrow();
            assertThat(role.getFilledCount()).isEqualTo(1);
            assertThat(role.getStatus()).isEqualTo(RecruitStatus.CLOSED);
            Post closed = postRepository.findById(post.getId()).orElseThrow();
            assertThat(closed.getStatus()).isEqualTo(RecruitStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();

            // 마감된 뒤 밥의 지원은 수락할 수 없음
            accept(leaderToken, bob)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));
        }

        @Test
        @DisplayName("다른 역할이 남아 있으면 역할만 마감되고 글은 모집중")
        void closesRoleOnly() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
            Long alice = applyOk(aliceToken, post.getId(), design.getId());
            Long bob = applyOk(bobToken, post.getId(), dev.getId());

            accept(leaderToken, alice)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roleClosed").value(true))
                    .andExpect(jsonPath("$.data.postClosed").value(false));
            // 2명 모집 역할은 1명 수락해도 아직 모집중
            accept(leaderToken, bob)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roleClosed").value(false))
                    .andExpect(jsonPath("$.data.postClosed").value(false));

            assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                    .isEqualTo(RecruitStatus.OPEN);
            PostRole devNow = postRoleRepository.findById(dev.getId()).orElseThrow();
            assertThat(devNow.getFilledCount()).isEqualTo(1);
            assertThat(devNow.getStatus()).isEqualTo(RecruitStatus.OPEN);
        }

        @Test
        @DisplayName("같은 지원을 두 번 수락하면 두 번째는 409, 인원은 한 번만 늘어난다")
        void acceptTwice() throws Exception {
            Post post = savePost(leaderId);
            PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 3);
            Long alice = applyOk(aliceToken, post.getId(), dev.getId());

            accept(leaderToken, alice).andExpect(status().isOk());
            accept(leaderToken, alice)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));

            assertThat(postRoleRepository.findById(dev.getId()).orElseThrow().getFilledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("자리가 이미 찼으면 409 ROLE_FULL이고 지원은 대기 상태로 남는다")
        void roleFullRollsBack() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            Long alice = applyOk(aliceToken, post.getId(), design.getId());
            // 동시에 다른 수락이 먼저 자리를 채운 상황을 흉내: 인원은 찼는데 역할은 아직 모집중
            ReflectionTestUtils.setField(design, "filledCount", 1);
            postRoleRepository.save(design);

            accept(leaderToken, alice)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROLE_FULL"));

            assertThat(statusOf(alice)).isEqualTo(ApplicationStatus.PENDING);
            assertThat(postRoleRepository.findById(design.getId()).orElseThrow().getFilledCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("팀장이 아니면 403, 없는 지원이면 404")
        void onlyLeader() throws Exception {
            Post post = savePost(leaderId);
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 1);
            Long alice = applyOk(aliceToken, post.getId(), design.getId());

            accept(bobToken, alice)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            accept(aliceToken, alice).andExpect(status().isForbidden()); // 본인 지원을 스스로 수락할 수 없음
            accept(leaderToken, 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            assertThat(statusOf(alice)).isEqualTo(ApplicationStatus.PENDING);
        }
    }

    // ===== APPL-06 거절 =====

    @Test
    @DisplayName("거절: 사유와 함께 또는 사유 없이 거절할 수 있고, 처리된 지원은 다시 거절할 수 없다")
    void reject() throws Exception {
        Post post = savePost(leaderId);
        PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
        Long alice = applyOk(aliceToken, post.getId(), dev.getId());
        Long bob = applyOk(bobToken, post.getId(), dev.getId());

        mockMvc.perform(patch("/api/applications/" + alice + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SCHEDULE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(alice))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        Application rejected = applicationRepository.findById(alice).orElseThrow();
        assertThat(rejected.getRejectReason()).isEqualTo(RejectReason.SCHEDULE);
        assertThat(rejected.getDecidedAt()).isNotNull();

        // 본문 없이도 거절 가능
        mockMvc.perform(patch("/api/applications/" + bob + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // 이미 거절한 지원은 다시 처리 불가, 거절은 인원을 채우지 않음
        mockMvc.perform(patch("/api/applications/" + alice + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));
        accept(leaderToken, alice).andExpect(status().isConflict());
        assertThat(postRoleRepository.findById(dev.getId()).orElseThrow().getFilledCount()).isZero();
    }

    @Test
    @DisplayName("거절: 팀장이 아니면 403, 없는 사유 코드는 400")
    void rejectForbiddenOrInvalid() throws Exception {
        Post post = savePost(leaderId);
        PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
        Long alice = applyOk(aliceToken, post.getId(), dev.getId());

        mockMvc.perform(patch("/api/applications/" + alice + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/applications/" + alice + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"NO_SUCH_REASON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(statusOf(alice)).isEqualTo(ApplicationStatus.PENDING);
    }

    // ===== APPL-07 연락 수단 =====

    @Test
    @DisplayName("연락 수단: 팀장과 수락된 지원자만 볼 수 있다")
    void contact() throws Exception {
        Post post = savePost(leaderId);
        PostRole dev = saveRole(post, RoleCode.DEV, "프론트 개발", 2);
        Long alice = applyOk(aliceToken, post.getId(), dev.getId());
        applyOk(bobToken, post.getId(), dev.getId());
        String url = "/api/posts/" + post.getId() + "/contact";

        // 수락 전에는 지원자도 볼 수 없음
        getWith(aliceToken, url)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        accept(leaderToken, alice).andExpect(status().isOk());

        getWith(aliceToken, url)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactLink").value(CONTACT));
        getWith(leaderToken, url)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactLink").value(CONTACT));
        // 아직 대기 중인 밥과 비회원은 볼 수 없음
        getWith(bobToken, url).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    // ===== APPL-10 내 지원 내역 =====

    @Test
    @DisplayName("내 지원 내역: 최신순, 상태로 거르기, 커서로 다음 페이지")
    void myApplications() throws Exception {
        Post first = savePost(leaderId);
        PostRole firstRole = saveRole(first, RoleCode.DESIGN, "UI 디자인", 2);
        Post second = savePost(leaderId);
        PostRole secondRole = saveRole(second, RoleCode.DEV, "프론트 개발", 2);
        Long older = applyOk(aliceToken, first.getId(), firstRole.getId());
        Long newer = applyOk(aliceToken, second.getId(), secondRole.getId());
        applyOk(bobToken, first.getId(), firstRole.getId()); // 다른 사람 지원은 안 보여야 함
        accept(leaderToken, older).andExpect(status().isOk());

        getWith(aliceToken, "/api/users/me/applications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].id").value(newer))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].roleName").value("프론트 개발"))
                .andExpect(jsonPath("$.data.items[0].post.id").value(second.getId()))
                .andExpect(jsonPath("$.data.items[0].post.deleted").value(false))
                .andExpect(jsonPath("$.data.items[1].id").value(older))
                .andExpect(jsonPath("$.data.items[1].status").value("ACCEPTED"));

        getWith(aliceToken, "/api/users/me/applications?status=ACCEPTED")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(older));

        // 한 개씩 넘기기
        String page1 = getWith(aliceToken, "/api/users/me/applications?size=1")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(newer))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode cursor = objectMapper.readTree(page1).at("/data/nextCursor");
        getWith(aliceToken, "/api/users/me/applications?size=1&cursor=" + cursor.asText())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(older))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        getWith(aliceToken, "/api/users/me/applications?status=NO_SUCH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
