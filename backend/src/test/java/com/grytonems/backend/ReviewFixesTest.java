package com.grytonems.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.notification.Notification;
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.post.EventRepository;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostDeadlineScheduler;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.report.ReportRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;

/**
 * 11/12 코드 점검에서 고친 것들의 테스트.
 * 검색어 특수문자, 잘못된 요청 형식의 500, 알림 30일 정리, 정지된 팀장 글의 D-1 알림·신고
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewFixesTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostDeadlineScheduler deadlineScheduler;
    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    School school;
    String leaderToken;
    Long leaderId;
    String aliceToken;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signupVerified("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signupVerified("alice@example.com", "앨리스");
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

    private Post savePost(String title, LocalDate deadline) {
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

    private void suspendLeader() {
        User leader = userRepository.findById(leaderId).orElseThrow();
        leader.suspend();
        userRepository.save(leader);
    }

    @Test
    @DisplayName("검색어의 %, _ 는 글자 그대로 찾는다 (모든 글과 맞지 않음)")
    void keywordEscapesWildcards() throws Exception {
        Post percent = savePost("참여율 100% 보장 프로젝트", LocalDate.now().plusDays(7));
        Post underscore = savePost("snake_case 좋아하는 개발자", LocalDate.now().plusDays(7));
        savePost("평범한 모집글 제목이에요", LocalDate.now().plusDays(7));

        // 고치기 전에는 "%%", "__"가 와일드카드라 세 글이 모두 나왔음
        mockMvc.perform(get("/api/posts").param("keyword", "%%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(get("/api/posts").param("keyword", "__"))
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(get("/api/posts").param("keyword", "0% 보장"))
                .andExpect(jsonPath("$.data.items[*].id", contains(percent.getId().intValue())));
        mockMvc.perform(get("/api/posts").param("keyword", "e_c"))
                .andExpect(jsonPath("$.data.items[*].id", contains(underscore.getId().intValue())));
    }

    @Test
    @DisplayName("JSON이 아닌 본문은 500이 아니라 400 INVALID_INPUT")
    void wrongContentTypeIs400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=leader@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("30일 지난 알림만 지운다")
    void deletesExpiredNotifications() {
        notificationService.notify(leaderId, NotificationType.NEW_APPLICATION, NotificationTargetType.POST, 1L, "오래된 알림");
        notificationService.notify(leaderId, NotificationType.NEW_APPLICATION, NotificationTargetType.POST, 1L, "최근 알림");
        Notification old = notificationRepository.findAll().stream()
                .filter(n -> n.getMessage().equals("오래된 알림")).findFirst().orElseThrow();
        // created_at은 저장 후 바뀌지 않게 막혀 있어서(updatable=false) SQL로 직접 31일 전으로 옮깁니다
        jdbcTemplate.update("update notifications set created_at = ? where id = ?",
                LocalDateTime.now().minusDays(31), old.getId());

        assertThat(notificationService.deleteExpired(LocalDateTime.now())).isEqualTo(1);
        assertThat(notificationRepository.findAll()).extracting(Notification::getMessage).containsExactly("최근 알림");
    }

    @Test
    @DisplayName("팀장이 정지 중인 글은 마감 D-1 알림을 보내지 않는다")
    void noDeadlineNoticeForSuspendedLeader() {
        savePost("내일 마감인 모집글이에요", LocalDate.now().plusDays(1));
        suspendLeader();

        assertThat(deadlineScheduler.notifyDeadlineTomorrowFor(LocalDate.now())).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("팀장이 정지 중인 글은 신고할 수 없다 (404)")
    void cannotReportSuspendedLeadersPost() throws Exception {
        Post post = savePost("정지된 팀장의 모집글", LocalDate.now().plusDays(7));
        suspendLeader();
        assertThat(userRepository.findById(leaderId).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);

        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("targetType", "POST", "targetId", post.getId(), "reasonCode", "FAKE"))))
                .andExpect(status().isNotFound());
    }
}
