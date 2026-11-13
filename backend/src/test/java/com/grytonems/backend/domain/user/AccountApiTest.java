package com.grytonems.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.application.Application;
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
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.profile.UserRoleTagRepository;
import com.grytonems.backend.domain.profile.UserSkillRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.AccountTestConfig.InMemoryResetTokenStore;
import com.grytonems.backend.domain.verification.VerificationTestConfig;
import com.grytonems.backend.domain.verification.VerificationTestConfig.RecordingEmailSender;

/** 닉네임 변경(AUTH-03), 비밀번호 재설정(AUTH-05), 회원 탈퇴(AUTH-09) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ VerificationTestConfig.class, AccountTestConfig.class })
class AccountApiTest {

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
    @Autowired UserRoleTagRepository roleTagRepository;
    @Autowired UserSkillRepository skillRepository;
    @Autowired RecordingEmailSender mailSender;
    @Autowired InMemoryResetTokenStore tokenStore;

    School school;

    @BeforeEach
    void setUp() {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        mailSender.clear();
        tokenStore.clear();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        applicationRepository.deleteAll();
        eventRepository.deleteAll();
        postRoleRepository.deleteAll();
        postRepository.deleteAll();
        roleTagRepository.deleteAll();
        skillRepository.deleteAll();
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

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", password)));
    }

    // ===== AUTH-03 =====

    @Test
    @DisplayName("닉네임 변경: 바뀌고 30일 동안 다시 못 바꿈, 중복·형식 오류")
    void changeNickname() throws Exception {
        String token = signup("alice@example.com", "앨리스", false);
        signup("bob@example.com", "밥이", false);

        as(token, get("/api/users/me")).andExpect(jsonPath("$.data.nicknameChangeableAt").value(nullValue()));

        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "밥이")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "공 백")))
                .andExpect(status().isBadRequest());
        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "a")))
                .andExpect(status().isBadRequest());

        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "새앨리스")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새앨리스"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value(LocalDate.now().plusDays(30).toString()));

        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "또바꿈")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_CHANGE_TOO_SOON"))
                .andExpect(jsonPath("$.message").value("30일 후에 바꿀 수 있어요"));

        // 30일이 지나면 다시 가능
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        ReflectionTestUtils.setField(user, "nicknameChangedAt", LocalDateTime.now().minusDays(30));
        userRepository.save(user);
        as(token, json(patch("/api/users/me/nickname"), Map.of("nickname", "또바꿈")))
                .andExpect(status().isOk());

        as(null, json(patch("/api/users/me/nickname"), Map.of("nickname", "비회원"))).andExpect(status().isUnauthorized());
    }

    // ===== AUTH-05 =====

    private String lastResetToken() {
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailSender.last().body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("비밀번호 재설정: 메일 링크로 새 비밀번호 저장, 링크는 한 번만, 없는 이메일도 200")
    void passwordReset() throws Exception {
        signup("alice@example.com", "앨리스", false);
        // 잠긴 계정도 재설정하면 풀림
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        ReflectionTestUtils.setField(user, "lockedUntil", LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        mockMvc.perform(json(post("/api/auth/password-reset"), Map.of("email", "nobody@example.com")))
                .andExpect(status().isOk());
        assertThat(mailSender.count()).isZero();

        mockMvc.perform(json(post("/api/auth/password-reset"), Map.of("email", "ALICE@example.com")))
                .andExpect(status().isOk());
        assertThat(mailSender.count()).isEqualTo(1);
        assertThat(mailSender.last().to()).isEqualTo("alice@example.com");
        assertThat(mailSender.last().body()).contains("http://localhost:5173/reset-password?token=");
        String token = lastResetToken();

        mockMvc.perform(json(post("/api/auth/password-reset/confirm"), Map.of("token", token, "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(json(post("/api/auth/password-reset/confirm"), Map.of("token", token, "newPassword", "newpass123")))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/auth/password-reset/confirm"), Map.of("token", token, "newPassword", "again1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));

        login("alice@example.com", "abcd1234").andExpect(status().isUnauthorized());
        login("alice@example.com", "newpass123").andExpect(status().isOk());
    }

    // ===== AUTH-09 =====

    private Post openPost(Long leaderId, String title) {
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

    private PostRole role(Post post, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(headcount).build());
    }

    private Long apply(String token, Post post, PostRole role) throws Exception {
        String body = as(token, json(post("/api/posts/" + post.getId() + "/applications"),
                        Map.of("postRoleId", role.getId(), "message", MESSAGE, "availableTime", "평일 저녁")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asLong();
    }

    @Test
    @DisplayName("탈퇴: 확인 수치, 내 글 마감·대기자 알림, 내 대기 지원 취소, 개인정보 삭제, 이후 401")
    void withdraw() throws Exception {
        String aliceToken = signup("alice@example.com", "앨리스", true);
        String bobToken = signup("bob@example.com", "밥이", true);
        String carolToken = signup("carol@example.com", "캐롤", true);
        Long aliceId = userId("alice@example.com");
        Long bobId = userId("bob@example.com");

        // 앨리스의 모집 중인 글에 캐롤이 대기 지원
        Post alicePost = openPost(aliceId, "앨리스의 프로젝트 모집");
        PostRole aliceRole = role(alicePost, 2);
        apply(carolToken, alicePost, aliceRole);
        // 밥의 글에 앨리스가 수락된 지원 1개, 대기 지원 1개
        Post bobPost = openPost(bobId, "밥의 프로젝트 모집입니다");
        PostRole bobRole = role(bobPost, 3);
        Long accepted = apply(aliceToken, bobPost, bobRole);
        as(bobToken, patch("/api/applications/" + accepted + "/accept")).andExpect(status().isOk());
        Post bobPost2 = openPost(bobId, "밥의 두 번째 모집글");
        PostRole bobRole2 = role(bobPost2, 1);
        Long pending = apply(aliceToken, bobPost2, bobRole2);
        notificationRepository.deleteAll();

        as(aliceToken, get("/api/users/me/withdrawal-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openPostCount").value(1))
                .andExpect(jsonPath("$.data.acceptedTeamCount").value(1));

        as(aliceToken, json(delete("/api/users/me"), Map.of("password", "wrong1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        as(aliceToken, json(delete("/api/users/me"), Map.of("password", "abcd1234")))
                .andExpect(status().isOk());

        User withdrawn = userRepository.findById(aliceId).orElseThrow();
        assertThat(withdrawn.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getSchoolEmail()).isNull();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
        assertThat(withdrawn.getNickname()).isEqualTo("탈퇴한 사용자");

        assertThat(postRepository.findById(alicePost.getId()).orElseThrow().getStatus()).isEqualTo(RecruitStatus.CLOSED);
        as(carolToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("POST_CLOSED"));
        Application canceled = applicationRepository.findById(pending).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(applicationRepository.findById(accepted).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ACCEPTED);

        // 이후 토큰은 401, 같은 이메일로 로그인 불가, 같은 이메일로 다시 가입 가능
        as(aliceToken, get("/api/users/me")).andExpect(status().isUnauthorized());
        login("alice@example.com", "abcd1234").andExpect(status().isUnauthorized());
        signup("alice@example.com", "새앨리스", false);
    }
}
