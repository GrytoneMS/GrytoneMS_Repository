package com.grytonems.backend.domain.notification;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 알림 통합 테스트 (NOTI-01 생성, NOTI-02 알림함) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationApiTest {

    private static final String TITLE = "충남 청년 아이디어 공모전 팀원 구해요";
    private static final String MESSAGE = "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationService notificationService;

    School school;
    String leaderToken;
    Long leaderId;
    String aliceToken;
    Long aliceId;
    String bobToken;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signupVerified("alice@example.com", "앨리스");
        aliceId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();
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

    private PostRole saveRole(Post post, String name, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName(name).headcount(headcount).build());
    }

    private Long apply(String token, Post post, PostRole role) throws Exception {
        String json = mockMvc.perform(post("/api/posts/" + post.getId() + "/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "postRoleId", role.getId(), "message", MESSAGE, "availableTime", "평일 저녁"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private ResultActions patchWith(String token, String url) throws Exception {
        return mockMvc.perform(patch(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions getWith(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void unreadCountIs(String token, int expected) throws Exception {
        getWith(token, "/api/notifications/unread-count")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(expected));
    }

    // ===== NOTI-01 알림 생성 =====

    @Test
    @DisplayName("지원하면 팀장에게 '새 지원 도착' 알림이 간다")
    void newApplicationNotifiesLeader() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 2);

        apply(aliceToken, post, role);

        getWith(leaderToken, "/api/notifications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.items[0].id").isNumber())
                .andExpect(jsonPath("$.data.items[0].type").value("NEW_APPLICATION"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("POST"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(post.getId()))
                .andExpect(jsonPath("$.data.items[0].message").value("앨리스님이 'UI 디자인'에 지원했어요"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty());
        unreadCountIs(leaderToken, 1);
        // 지원한 본인에게는 알림이 없음
        unreadCountIs(aliceToken, 0);
    }

    @Test
    @DisplayName("수락하면 지원자에게 합류 알림, 같은 역할에 남아 있던 대기 지원자에게는 마감 알림이 간다")
    void acceptNotifiesApplicantAndClosesOthers() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 1);
        Long alice = apply(aliceToken, post, role);
        apply(bobToken, post, role);

        patchWith(leaderToken, "/api/applications/" + alice + "/accept").andExpect(status().isOk());

        getWith(aliceToken, "/api/notifications")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(post.getId()))
                .andExpect(jsonPath("$.data.items[0].message")
                        .value("축하해요! '" + TITLE + "' 팀에 합류했어요. 연락 수단을 확인하세요"));
        getWith(bobToken, "/api/notifications")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("POST_CLOSED"))
                .andExpect(jsonPath("$.data.items[0].message").value("'" + TITLE + "' 모집이 마감되었어요"));
        // 팀장은 지원 2건의 알림만 받음
        unreadCountIs(leaderToken, 2);
    }

    @Test
    @DisplayName("자리가 남아 역할이 마감되지 않으면 다른 대기 지원자에게 마감 알림이 가지 않는다")
    void noCloseNotificationWhileOpen() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 2);
        Long alice = apply(aliceToken, post, role);
        apply(bobToken, post, role);

        patchWith(leaderToken, "/api/applications/" + alice + "/accept").andExpect(status().isOk());

        unreadCountIs(aliceToken, 1);
        unreadCountIs(bobToken, 0);
    }

    @Test
    @DisplayName("거절하면 지원자에게 결과 확인 알림이 간다")
    void rejectNotifiesApplicant() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 2);
        Long alice = apply(aliceToken, post, role);

        patchWith(leaderToken, "/api/applications/" + alice + "/reject").andExpect(status().isOk());

        getWith(aliceToken, "/api/notifications")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("REJECTED"))
                .andExpect(jsonPath("$.data.items[0].message").value("'" + TITLE + "' 지원 결과를 확인하세요"));
    }

    @Test
    @DisplayName("수락이 실패하면(이미 처리된 지원) 알림도 만들어지지 않는다")
    void failedAcceptCreatesNoNotification() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 2);
        Long alice = apply(aliceToken, post, role);
        patchWith(leaderToken, "/api/applications/" + alice + "/accept").andExpect(status().isOk());

        patchWith(leaderToken, "/api/applications/" + alice + "/accept").andExpect(status().isConflict());

        unreadCountIs(aliceToken, 1);
    }

    // ===== NOTI-02 알림함 =====

    @Test
    @DisplayName("알림 하나를 읽음 처리하면 안 읽은 개수가 줄고, 남의 알림은 읽음 처리할 수 없다")
    void markRead() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 3);
        apply(aliceToken, post, role);
        apply(bobToken, post, role);
        unreadCountIs(leaderToken, 2);

        String json = getWith(leaderToken, "/api/notifications").andReturn().getResponse().getContentAsString();
        long newest = objectMapper.readTree(json).at("/data/items/0/id").asLong();

        // 남의 알림 → 404, 상태 변화 없음
        patchWith(aliceToken, "/api/notifications/" + newest + "/read")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        unreadCountIs(leaderToken, 2);

        patchWith(leaderToken, "/api/notifications/" + newest + "/read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        unreadCountIs(leaderToken, 1);
        getWith(leaderToken, "/api/notifications")
                .andExpect(jsonPath("$.data.items[0].read").value(true))
                .andExpect(jsonPath("$.data.items[1].read").value(false));

        // 다시 눌러도 문제없음
        patchWith(leaderToken, "/api/notifications/" + newest + "/read").andExpect(status().isOk());
        unreadCountIs(leaderToken, 1);
    }

    @Test
    @DisplayName("모두 읽음은 내 알림만 읽음 처리한다")
    void markAllRead() throws Exception {
        Post post = savePost();
        PostRole role = saveRole(post, "UI 디자인", 3);
        Long alice = apply(aliceToken, post, role);
        apply(bobToken, post, role);
        patchWith(leaderToken, "/api/applications/" + alice + "/accept").andExpect(status().isOk());
        unreadCountIs(leaderToken, 2);
        unreadCountIs(aliceToken, 1);

        patchWith(leaderToken, "/api/notifications/read-all").andExpect(status().isOk());

        unreadCountIs(leaderToken, 0);
        unreadCountIs(aliceToken, 1);
    }

    @Test
    @DisplayName("최신순으로 나오고 커서로 다음 페이지를 받는다")
    void pagination() throws Exception {
        for (int i = 1; i <= 3; i++) {
            notificationService.notify(aliceId, NotificationType.DEADLINE_SOON, NotificationTargetType.POST,
                    (long) i, "알림 " + i);
        }

        String page1 = getWith(aliceToken, "/api/notifications?size=2")
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].message").value("알림 3"))
                .andExpect(jsonPath("$.data.items[1].message").value("알림 2"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn().getResponse().getContentAsString();
        String cursor = objectMapper.readTree(page1).at("/data/nextCursor").asText();

        getWith(aliceToken, "/api/notifications?size=2&cursor=" + cursor)
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message").value("알림 1"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("30일이 지난 알림은 목록과 안 읽은 개수에서 빠진다")
    void retention() throws Exception {
        notificationService.notify(aliceId, NotificationType.DEADLINE_SOON, NotificationTargetType.POST, 1L, "오래된 알림");
        jdbcTemplate.update("update notifications set created_at = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(NotificationService.RETENTION_DAYS + 1)));
        notificationService.notify(aliceId, NotificationType.DEADLINE_SOON, NotificationTargetType.POST, 2L, "새 알림");

        getWith(aliceToken, "/api/notifications")
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].message").value("새 알림"));
        unreadCountIs(aliceToken, 1);
    }

    @Test
    @DisplayName("비회원은 401, 알림이 없으면 빈 목록")
    void anonymousAndEmpty() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());

        getWith(aliceToken, "/api/notifications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        unreadCountIs(aliceToken, 0);
        patchWith(aliceToken, "/api/notifications/999999/read").andExpect(status().isNotFound());
    }
}
