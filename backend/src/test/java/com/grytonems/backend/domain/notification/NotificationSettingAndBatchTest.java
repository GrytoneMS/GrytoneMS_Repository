package com.grytonems.backend.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.application.ApplicationReminderService;
import com.grytonems.backend.domain.application.ApplicationRepository;
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
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.verification.ReverifyService;

/** 알림 설정(NOTI-03), 학기 재인증 배치(AUTH-08), 대기 지원 리마인드(APPL-12) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSettingAndBatchTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationSettingRepository settingRepository;
    @Autowired ReverifyService reverifyService;
    @Autowired ApplicationReminderService reminderService;
    @Autowired JdbcTemplate jdbcTemplate;

    School school;
    String aliceToken;
    String bobToken;
    Long aliceId;
    Long bobId;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN).build());
        aliceToken = signupVerified("alice@example.com", "앨리스");
        bobToken = signupVerified("bob@example.com", "밥이");
        aliceId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@example.com").orElseThrow().getId();
    }

    @AfterEach
    void cleanUp() {
        settingRepository.deleteAll();
        notificationRepository.deleteAll();
        applicationRepository.deleteAll();
        eventRepository.deleteAll();
        postRoleRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

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

    @Test
    @DisplayName("알림 설정: 처음엔 모두 켜짐, 저장 후 그대로 조회, 값 빠지면 400")
    void settings() throws Exception {
        as(aliceToken, get("/api/users/me/notification-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyEnabled").value(true))
                .andExpect(jsonPath("$.data.deadlineEnabled").value(true))
                .andExpect(jsonPath("$.data.quietHoursEnabled").value(true));

        Map<String, Object> body = Map.of("applyEnabled", false, "deadlineEnabled", true, "quietHoursEnabled", false);
        as(aliceToken, put("/api/users/me/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applyEnabled").value(false));
        as(aliceToken, put("/api/users/me/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        as(aliceToken, get("/api/users/me/notification-settings"))
                .andExpect(jsonPath("$.data.applyEnabled").value(false))
                .andExpect(jsonPath("$.data.deadlineEnabled").value(true))
                .andExpect(jsonPath("$.data.quietHoursEnabled").value(false));
        as(bobToken, get("/api/users/me/notification-settings")).andExpect(jsonPath("$.data.applyEnabled").value(true));

        as(aliceToken, put("/api/users/me/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"applyEnabled\":true}"))
                .andExpect(status().isBadRequest());
        as(null, get("/api/users/me/notification-settings")).andExpect(status().isUnauthorized());
        assertThat(settingRepository.count()).isEqualTo(1);
    }

    private void setVerifiedAt(Long userId, LocalDateTime verifiedAt) {
        User user = userRepository.findById(userId).orElseThrow();
        ReflectionTestUtils.setField(user, "verifiedAt", verifiedAt);
        userRepository.save(user);
    }

    @Test
    @DisplayName("학기 재인증: 3/1에 이전 학기 인증자에게만 알림, 기한(3/14) 다음 날부터 미인증")
    void reverify() throws Exception {
        setVerifiedAt(aliceId, LocalDateTime.of(2027, 2, 10, 12, 0)); // 지난 학기 인증 → 재인증 대상
        setVerifiedAt(bobId, LocalDateTime.of(2027, 3, 1, 8, 0));    // 이번 학기 인증 → 대상 아님

        assertThat(reverifyService.notifyReverify(LocalDate.of(2027, 2, 28))).isZero();
        assertThat(reverifyService.notifyReverify(LocalDate.of(2027, 3, 1))).isEqualTo(1);
        as(aliceToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("REVERIFY_REQUIRED"))
                .andExpect(jsonPath("$.data.items[0].message").value("이번 학기 학교 인증을 다시 해주세요 (3월 14일까지)"));
        as(bobToken, get("/api/notifications")).andExpect(jsonPath("$.data.items", hasSize(0)));

        assertThat(reverifyService.expire(LocalDate.of(2027, 3, 14))).isZero();
        assertThat(userRepository.findById(aliceId).orElseThrow().getStatus()).isEqualTo(UserStatus.VERIFIED);

        assertThat(reverifyService.expire(LocalDate.of(2027, 3, 15))).isEqualTo(1);
        assertThat(userRepository.findById(aliceId).orElseThrow().getStatus()).isEqualTo(UserStatus.UNVERIFIED);
        assertThat(userRepository.findById(bobId).orElseThrow().getStatus()).isEqualTo(UserStatus.VERIFIED);

        // 미인증이 되면 글쓰기·지원이 막힘
        as(aliceToken, get("/api/users/me")).andExpect(jsonPath("$.data.status").value("UNVERIFIED"));
    }

    @Test
    @DisplayName("대기 지원 리마인드: 3일 지난 대기 지원이 있는 글의 팀장에게 글마다 한 건, 같은 지원은 한 번만")
    void reminder() throws Exception {
        LocalDate deadline = LocalDate.now().plusDays(7);
        Post post = postRepository.save(Post.builder()
                .leaderId(aliceId)
                .title("앨리스의 프로젝트 모집")
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
        PostRole role = postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(2).build());

        String json = as(bobToken, post("/api/posts/" + post.getId() + "/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("postRoleId", role.getId(),
                                "message", "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.",
                                "availableTime", "평일 저녁"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(json).at("/data/id").asLong();
        notificationRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        assertThat(reminderService.remind(now)).isZero(); // 아직 3일이 안 됨

        jdbcTemplate.update("update applications set created_at = ? where id = ?", now.minusDays(3).minusMinutes(1),
                applicationId);
        assertThat(reminderService.remind(now)).isEqualTo(1);
        as(aliceToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("APPLICATION_REMINDER"))
                .andExpect(jsonPath("$.data.items[0].message").value("아직 확인하지 않은 지원자가 1명 있어요"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(post.getId()));
        assertThat(applicationRepository.findById(applicationId).orElseThrow().getRemindedAt()).isNotNull();

        // 같은 지원으로는 다시 보내지 않음
        assertThat(reminderService.remind(now.plusDays(1))).isZero();
    }
}
