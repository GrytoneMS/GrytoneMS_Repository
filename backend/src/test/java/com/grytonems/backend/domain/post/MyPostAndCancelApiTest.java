package com.grytonems.backend.domain.post;

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
 * 마이페이지 API 테스트: 지원 취소 (APPL-03), 내 모집글 (APPL-11)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPostAndCancelApiTest {

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
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signupVerified("alice@example.com", "앨리스");
        bobToken = signupVerified("bob@example.com", "밥이");
        carolToken = signupVerified("carol@example.com", "캐롤");
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

    private PostRole saveRole(Post post, RoleCode code, String name, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(name).headcount(headcount).build());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions apply(String token, Long postId, Long roleId) throws Exception {
        return as(token, post("/api/posts/" + postId + "/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("postRoleId", roleId, "message", MESSAGE, "availableTime", "평일 저녁"))));
    }

    private Long applyOk(String token, Long postId, Long roleId) throws Exception {
        String json = apply(token, postId, roleId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private ResultActions cancel(String token, Long applicationId) throws Exception {
        return as(token, patch("/api/applications/" + applicationId + "/cancel"));
    }

    // ===== APPL-03 지원 취소 =====

    @Nested
    @DisplayName("지원 취소 PATCH /api/applications/{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("대기 중인 내 지원을 취소하면 CANCELED, 지원자 목록에서 빠지고 같은 글에 재지원 불가")
        void success() throws Exception {
            Post post = savePost(leaderId, "디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            Long applicationId = applyOk(aliceToken, post.getId(), design.getId());

            cancel(aliceToken, applicationId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                    .andExpect(jsonPath("$.data.status").value("CANCELED"));

            assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.CANCELED);
            as(leaderToken, get("/api/posts/" + post.getId() + "/applications"))
                    .andExpect(jsonPath("$.data[0].applications", hasSize(0)));
            apply(aliceToken, post.getId(), design.getId())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
            as(aliceToken, get("/api/users/me/applications?status=CANCELED"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(applicationId.intValue())));
        }

        @Test
        @DisplayName("취소한 지원은 팀장이 수락할 수 없다 (409)")
        void cannotAcceptCanceled() throws Exception {
            Post post = savePost(leaderId, "디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            Long applicationId = applyOk(aliceToken, post.getId(), design.getId());
            cancel(aliceToken, applicationId).andExpect(status().isOk());

            as(leaderToken, patch("/api/applications/" + applicationId + "/accept"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));
        }

        @Test
        @DisplayName("수락·거절된 지원과 이미 취소한 지원은 409")
        void onlyPending() throws Exception {
            Post post = savePost(leaderId, "디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 3);
            Long accepted = applyOk(aliceToken, post.getId(), design.getId());
            Long rejected = applyOk(bobToken, post.getId(), design.getId());
            Long canceled = applyOk(carolToken, post.getId(), design.getId());
            as(leaderToken, patch("/api/applications/" + accepted + "/accept")).andExpect(status().isOk());
            as(leaderToken, patch("/api/applications/" + rejected + "/reject")).andExpect(status().isOk());
            cancel(carolToken, canceled).andExpect(status().isOk());

            cancel(aliceToken, accepted).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));
            cancel(bobToken, rejected).andExpect(status().isConflict());
            cancel(carolToken, canceled).andExpect(status().isConflict());
            assertThat(applicationRepository.findById(accepted).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("남의 지원은 403 (팀장도 불가), 없는 지원은 404, 비회원은 401")
        void ownerOnly() throws Exception {
            Post post = savePost(leaderId, "디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            Long applicationId = applyOk(aliceToken, post.getId(), design.getId());

            cancel(bobToken, applicationId).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            cancel(leaderToken, applicationId).andExpect(status().isForbidden());
            cancel(aliceToken, 999999L).andExpect(status().isNotFound());
            cancel(null, applicationId).andExpect(status().isUnauthorized());
            assertThat(applicationRepository.findById(applicationId).orElseThrow().getStatus())
                    .isEqualTo(ApplicationStatus.PENDING);
        }
    }

    // ===== APPL-11 내 모집글 =====

    @Nested
    @DisplayName("내 모집글 GET /api/users/me/posts")
    class MyPosts {

        @Test
        @DisplayName("역할별·전체 지원자 수(취소 제외)와 대기 수, 인원·상태가 담긴다")
        void counts() throws Exception {
            Post post = savePost(leaderId, "디자이너 구해요 프로젝트");
            PostRole design = saveRole(post, RoleCode.DESIGN, "UI 디자인", 2);
            PostRole dev = saveRole(post, RoleCode.DEV, "백엔드", 1);
            Long accepted = applyOk(aliceToken, post.getId(), design.getId());
            applyOk(bobToken, post.getId(), design.getId());
            Long canceled = applyOk(carolToken, post.getId(), dev.getId());
            as(leaderToken, patch("/api/applications/" + accepted + "/accept")).andExpect(status().isOk());
            cancel(carolToken, canceled).andExpect(status().isOk());

            as(leaderToken, get("/api/users/me/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].id").value(post.getId()))
                    .andExpect(jsonPath("$.data.items[0].title").value("디자이너 구해요 프로젝트"))
                    .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.data.items[0].dDay").value(7))
                    .andExpect(jsonPath("$.data.items[0].applicantCount").value(2))
                    .andExpect(jsonPath("$.data.items[0].pendingCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].roles[0].id").value(design.getId()))
                    .andExpect(jsonPath("$.data.items[0].roles[0].headcount").value(2))
                    .andExpect(jsonPath("$.data.items[0].roles[0].filledCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].roles[0].applicantCount").value(2))
                    .andExpect(jsonPath("$.data.items[0].roles[0].pendingCount").value(1))
                    .andExpect(jsonPath("$.data.items[0].roles[1].roleName").value("백엔드"))
                    .andExpect(jsonPath("$.data.items[0].roles[1].applicantCount").value(0))
                    .andExpect(jsonPath("$.data.items[0].roles[1].pendingCount").value(0));
        }

        @Test
        @DisplayName("모집중·마감 탭으로 거르고, 남의 글·숨김 글·삭제된 글은 빠진다")
        void tabs() throws Exception {
            Post open = savePost(leaderId, "모집 중인 내 모집글");
            Post closed = savePost(leaderId, "마감한 내 모집글이에요");
            as(leaderToken, patch("/api/posts/" + closed.getId() + "/close")).andExpect(status().isOk());
            Post hidden = savePost(leaderId, "숨김 처리된 내 모집글");
            hidden.hide();
            postRepository.save(hidden);
            Post deleted = savePost(leaderId, "삭제한 내 모집글이에요");
            ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
            postRepository.save(deleted);
            savePost(userRepository.findByEmail("alice@example.com").orElseThrow().getId(), "앨리스의 모집글");

            as(leaderToken, get("/api/users/me/posts")).andExpect(jsonPath("$.data.items[*].id",
                    contains(closed.getId().intValue(), open.getId().intValue())));
            as(leaderToken, get("/api/users/me/posts?status=OPEN"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(open.getId().intValue())));
            as(leaderToken, get("/api/users/me/posts?status=CLOSED"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(closed.getId().intValue())))
                    .andExpect(jsonPath("$.data.items[0].closedAt").isNotEmpty());
        }

        @Test
        @DisplayName("최신순, size와 nextCursor로 페이지 넘김")
        void cursor() throws Exception {
            Post first = savePost(leaderId, "첫 번째 내 모집글");
            Post second = savePost(leaderId, "두 번째 내 모집글");
            Post third = savePost(leaderId, "세 번째 내 모집글");

            String json = as(leaderToken, get("/api/users/me/posts?size=2"))
                    .andExpect(jsonPath("$.data.items[*].id",
                            contains(third.getId().intValue(), second.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = objectMapper.readTree(json).at("/data/nextCursor").asText();

            as(leaderToken, get("/api/users/me/posts?size=2&cursor=" + cursor))
                    .andExpect(jsonPath("$.data.items[*].id", contains(first.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
        }

        @Test
        @DisplayName("글이 없으면 빈 목록, 비회원은 401, 잘못된 탭 값은 400")
        void emptyAndAuth() throws Exception {
            as(leaderToken, get("/api/users/me/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items", hasSize(0)))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
            as(null, get("/api/users/me/posts")).andExpect(status().isUnauthorized());
            as(leaderToken, get("/api/users/me/posts?status=DONE")).andExpect(status().isBadRequest());
        }
    }
}
