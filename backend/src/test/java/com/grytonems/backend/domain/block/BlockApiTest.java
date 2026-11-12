package com.grytonems.backend.domain.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import com.grytonems.backend.domain.scrap.ScrapRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/**
 * 차단 API 테스트 (SAFE-03 차단·해제·목록, LIST-06 목록 제외, 상세·지원·프로필·스크랩 반영)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlockApiTest {

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
    @Autowired BlockRepository blockRepository;

    School school;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    Long aliceId;
    String bobToken;
    Long bobId;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = idOf("leader@example.com");
        aliceToken = signupVerified("alice@example.com", "앨리스");
        aliceId = idOf("alice@example.com");
        bobToken = signupVerified("bob@example.com", "밥이");
        bobId = idOf("bob@example.com");
    }

    @AfterEach
    void cleanUp() {
        blockRepository.deleteAll();
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

    private ResultActions block(String token, Long userId) throws Exception {
        return as(token, post("/api/blocks").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("userId", userId))));
    }

    private ResultActions unblock(String token, Long userId) throws Exception {
        return as(token, delete("/api/blocks/" + userId));
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

    private ApplicationStatus statusOf(Long applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow().getStatus();
    }

    // ===== 차단·해제·목록 =====

    @Nested
    @DisplayName("차단·해제·내 차단 목록")
    class Basics {

        @Test
        @DisplayName("차단하면 isBlocked=true, 두 번 해도 한 번만 저장. 해제하면 false, 두 번 해도 성공")
        void blockAndUnblock() throws Exception {
            block(aliceToken, leaderId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(leaderId))
                    .andExpect(jsonPath("$.data.isBlocked").value(true));
            block(aliceToken, leaderId).andExpect(status().isOk());
            assertThat(blockRepository.count()).isEqualTo(1);

            unblock(aliceToken, leaderId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isBlocked").value(false));
            unblock(aliceToken, leaderId).andExpect(status().isOk());
            assertThat(blockRepository.count()).isZero();
        }

        @Test
        @DisplayName("나 자신은 400, 없는 사용자는 404, 비회원은 401")
        void errors() throws Exception {
            block(aliceToken, aliceId)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
            block(aliceToken, 999999L).andExpect(status().isNotFound());
            block(null, leaderId).andExpect(status().isUnauthorized());
            unblock(null, leaderId).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("내 차단 목록: 최근 차단순, 학교 이름, 커서")
        void myBlocks() throws Exception {
            block(aliceToken, leaderId).andExpect(status().isOk());
            block(aliceToken, bobId).andExpect(status().isOk());

            String json = as(aliceToken, get("/api/users/me/blocks?size=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].userId", contains(bobId.intValue())))
                    .andExpect(jsonPath("$.data.items[0].nickname").value("밥이"))
                    .andExpect(jsonPath("$.data.items[0].schoolName").value("단국대학교"))
                    .andExpect(jsonPath("$.data.items[0].blockedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = objectMapper.readTree(json).at("/data/nextCursor").asText();

            as(aliceToken, get("/api/users/me/blocks?size=1&cursor=" + cursor))
                    .andExpect(jsonPath("$.data.items[*].userId", contains(leaderId.intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
            as(leaderToken, get("/api/users/me/blocks")).andExpect(jsonPath("$.data.items", hasSize(0)));
        }
    }

    // ===== 차단 반영 =====

    @Nested
    @DisplayName("차단 관계 반영")
    class Effects {

        @Test
        @DisplayName("목록: 차단한 쪽도 차단당한 쪽도 서로의 글이 빠지고, 다른 사람·비회원은 그대로")
        void list() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            Post alicePost = savePost(aliceId, "앨리스가 올린 모집글");
            block(aliceToken, leaderId).andExpect(status().isOk());

            as(aliceToken, get("/api/posts"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(alicePost.getId().intValue())));
            as(leaderToken, get("/api/posts"))
                    .andExpect(jsonPath("$.data.items[*].id", contains(leaderPost.getId().intValue())));
            as(bobToken, get("/api/posts")).andExpect(jsonPath("$.data.items[*].id",
                    containsInAnyOrder(leaderPost.getId().intValue(), alicePost.getId().intValue())));
            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(2)));
        }

        @Test
        @DisplayName("상세·프로필: 내가 차단했으면 403 BLOCKED_USER, 나를 차단했으면 404")
        void detailAndProfile() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            Post alicePost = savePost(aliceId, "앨리스가 올린 모집글");
            block(aliceToken, leaderId).andExpect(status().isOk());

            as(aliceToken, get("/api/posts/" + leaderPost.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
            as(leaderToken, get("/api/posts/" + alicePost.getId()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            as(null, get("/api/posts/" + leaderPost.getId())).andExpect(status().isOk());
            as(bobToken, get("/api/posts/" + leaderPost.getId())).andExpect(status().isOk());

            as(aliceToken, get("/api/users/" + leaderId + "/profile"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
            as(leaderToken, get("/api/users/" + aliceId + "/profile")).andExpect(status().isNotFound());
            as(bobToken, get("/api/users/" + aliceId + "/profile")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("지원: 차단 관계인 팀장의 글에는 지원 불가 (상세와 같은 오류)")
        void apply() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            PostRole role = saveRole(leaderPost);
            block(aliceToken, leaderId).andExpect(status().isOk());
            block(leaderToken, bobId).andExpect(status().isOk());

            apply(aliceToken, leaderPost.getId(), role.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("BLOCKED_USER"));
            apply(bobToken, leaderPost.getId(), role.getId()).andExpect(status().isNotFound());
            assertThat(applicationRepository.count()).isZero();
        }

        @Test
        @DisplayName("차단하는 순간 둘 사이의 대기 지원(양쪽)은 알림 없이 마감, 수락된 팀은 그대로")
        void closesPendingBothWays() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            PostRole leaderRole = saveRole(leaderPost);
            Post alicePost = savePost(aliceId, "앨리스가 올린 모집글");
            PostRole aliceRole = saveRole(alicePost);
            Post bobPost = savePost(bobId, "밥이 올린 모집글이에요");
            PostRole bobRole = saveRole(bobPost);

            Long aliceToLeader = applyOk(aliceToken, leaderPost.getId(), leaderRole.getId());
            Long leaderToAlice = applyOk(leaderToken, alicePost.getId(), aliceRole.getId());
            Long bobToLeader = applyOk(bobToken, leaderPost.getId(), leaderRole.getId());
            Long aliceToBob = applyOk(aliceToken, bobPost.getId(), bobRole.getId());
            Long leaderToBob = applyOk(leaderToken, bobPost.getId(), bobRole.getId());
            as(bobToken, patch("/api/applications/" + leaderToBob + "/accept")).andExpect(status().isOk());
            notificationRepository.deleteAll();

            block(aliceToken, leaderId).andExpect(status().isOk());

            assertThat(statusOf(aliceToLeader)).isEqualTo(ApplicationStatus.CLOSED);
            assertThat(statusOf(leaderToAlice)).isEqualTo(ApplicationStatus.CLOSED);
            assertThat(statusOf(bobToLeader)).isEqualTo(ApplicationStatus.PENDING);
            assertThat(statusOf(aliceToBob)).isEqualTo(ApplicationStatus.PENDING);
            assertThat(statusOf(leaderToBob)).isEqualTo(ApplicationStatus.ACCEPTED);
            assertThat(notificationRepository.count()).isZero();
        }

        @Test
        @DisplayName("이미 수락된 팀은 차단 뒤에도 연락 수단을 볼 수 있다")
        void acceptedTeamUnaffected() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            PostRole role = saveRole(leaderPost);
            Long applicationId = applyOk(aliceToken, leaderPost.getId(), role.getId());
            as(leaderToken, patch("/api/applications/" + applicationId + "/accept")).andExpect(status().isOk());

            block(leaderToken, aliceId).andExpect(status().isOk());

            assertThat(statusOf(applicationId)).isEqualTo(ApplicationStatus.ACCEPTED);
            as(aliceToken, get("/api/posts/" + leaderPost.getId() + "/contact")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("내 스크랩에서도 차단 관계인 사람의 글이 빠진다")
        void scraps() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            Post bobPost = savePost(bobId, "밥이 올린 모집글이에요");
            as(aliceToken, put("/api/posts/" + leaderPost.getId() + "/scrap")).andExpect(status().isOk());
            as(aliceToken, put("/api/posts/" + bobPost.getId() + "/scrap")).andExpect(status().isOk());

            block(leaderToken, aliceId).andExpect(status().isOk());

            as(aliceToken, get("/api/users/me/scraps"))
                    .andExpect(jsonPath("$.data.items[*].post.id", contains(bobPost.getId().intValue())));
        }

        @Test
        @DisplayName("해제하면 다시 보이지만, 차단 때 마감된 지원은 되돌리지 않는다")
        void unblockRestoresVisibility() throws Exception {
            Post leaderPost = savePost(leaderId, "팀장이 올린 모집글");
            PostRole role = saveRole(leaderPost);
            Long applicationId = applyOk(aliceToken, leaderPost.getId(), role.getId());
            block(aliceToken, leaderId).andExpect(status().isOk());

            unblock(aliceToken, leaderId).andExpect(status().isOk());

            as(aliceToken, get("/api/posts/" + leaderPost.getId())).andExpect(status().isOk());
            as(aliceToken, get("/api/posts")).andExpect(jsonPath("$.data.items", hasSize(1)));
            assertThat(statusOf(applicationId)).isEqualTo(ApplicationStatus.CLOSED);
        }
    }
}
