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
 * 팀 나가기·내보내기 테스트 (APPL-09)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamLeaveApiTest {

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

    Post post;
    PostRole role;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signupVerified("alice@example.com", "앨리스");
        bobToken = signupVerified("bob@example.com", "밥이");

        LocalDate deadline = LocalDate.now().plusDays(7);
        post = postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title("디자이너 구해요 프로젝트")
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
        // 한 명만 뽑는 역할: 수락하면 역할과 글이 함께 마감됨
        role = postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(1).build());
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

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private Long apply(String token) throws Exception {
        String json = as(token, post("/api/posts/" + post.getId() + "/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("postRoleId", role.getId(), "message", MESSAGE, "availableTime", "평일 저녁"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private Long acceptedAlice() throws Exception {
        Long applicationId = apply(aliceToken);
        as(leaderToken, patch("/api/applications/" + applicationId + "/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postClosed").value(true));
        notificationRepository.deleteAll();
        return applicationId;
    }

    private ResultActions leave(String token, Long applicationId) throws Exception {
        return as(token, patch("/api/applications/" + applicationId + "/leave"));
    }

    @Test
    @DisplayName("본인이 나가면 LEFT, 자리가 비고 역할·글이 다시 모집중, 팀장에게 MEMBER_LEFT, 연락 수단은 못 봄")
    void memberLeaves() throws Exception {
        Long applicationId = acceptedAlice();

        leave(aliceToken, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationId").value(applicationId))
                .andExpect(jsonPath("$.data.status").value("LEFT"))
                .andExpect(jsonPath("$.data.roleReopened").value(true))
                .andExpect(jsonPath("$.data.postReopened").value(true));

        Application left = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(left.getStatus()).isEqualTo(ApplicationStatus.LEFT);
        assertThat(left.getLeftAt()).isNotNull();
        PostRole reloadedRole = postRoleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloadedRole.getFilledCount()).isZero();
        assertThat(reloadedRole.getStatus()).isEqualTo(RecruitStatus.OPEN);
        Post reloadedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(reloadedPost.getStatus()).isEqualTo(RecruitStatus.OPEN);
        assertThat(reloadedPost.getClosedAt()).isNull();

        as(leaderToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEMBER_LEFT"))
                .andExpect(jsonPath("$.data.items[0].message").value("앨리스님이 팀을 나갔어요. 자리가 다시 열렸어요"));
        as(aliceToken, get("/api/posts/" + post.getId() + "/contact")).andExpect(status().isForbidden());
        as(aliceToken, get("/api/users/me/applications?status=LEFT"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));

        // 다시 모집중이 됐으니 다른 사람이 지원할 수 있음
        apply(bobToken);
    }

    @Test
    @DisplayName("팀장이 내보내면 팀원에게 MEMBER_REMOVED")
    void leaderRemoves() throws Exception {
        Long applicationId = acceptedAlice();

        leave(leaderToken, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LEFT"));

        as(aliceToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.data.items[0].message").value("'디자이너 구해요 프로젝트' 팀에서 나오게 됐어요"));
        as(leaderToken, get("/api/notifications")).andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("마감일이 지났으면 자리만 비고 역할·글은 마감 그대로")
    void afterDeadline() throws Exception {
        Long applicationId = acceptedAlice();
        Post loaded = postRepository.findById(post.getId()).orElseThrow();
        ReflectionTestUtils.setField(loaded, "deadline", LocalDate.now().minusDays(1));
        postRepository.save(loaded);

        leave(aliceToken, applicationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleReopened").value(false))
                .andExpect(jsonPath("$.data.postReopened").value(false));

        assertThat(postRoleRepository.findById(role.getId()).orElseThrow().getFilledCount()).isZero();
        assertThat(postRoleRepository.findById(role.getId()).orElseThrow().getStatus()).isEqualTo(RecruitStatus.CLOSED);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus()).isEqualTo(RecruitStatus.CLOSED);
        as(leaderToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].message").value("앨리스님이 팀을 나갔어요"));
    }

    @Test
    @DisplayName("수락 상태가 아니면 409, 본인·팀장이 아니면 403, 비회원 401, 없는 지원 404")
    void errors() throws Exception {
        Long pending = apply(bobToken);
        leave(bobToken, pending)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));

        Long applicationId = acceptedAlice();
        leave(bobToken, applicationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        leave(null, applicationId).andExpect(status().isUnauthorized());
        leave(aliceToken, 999999L).andExpect(status().isNotFound());

        leave(aliceToken, applicationId).andExpect(status().isOk());
        leave(aliceToken, applicationId).andExpect(status().isConflict());
    }
}
