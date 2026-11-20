package com.grytonems.backend.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/**
 * 모집글 수정 테스트 (POST-02)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostUpdateApiTest {

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

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        leaderToken = signup("leader@example.com", "팀장");
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signup("alice@example.com", "앨리스");
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
        User user = userRepository.findByEmail(email).orElseThrow();
        user.verify(school, email.replace("@example.com", "@dankook.ac.kr"), LocalDateTime.now());
        userRepository.save(user);
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private Post savePost() {
        LocalDate deadline = LocalDate.now().plusDays(7);
        return postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title("수정 전 모집글 제목")
                .category(PostCategory.PROJECT)
                .description("아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink(CONTACT)
                .build());
    }

    private PostRole saveRole(Post post, RoleCode code, int headcount) {
        return postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(code).roleName(code.name()).headcount(headcount).build());
    }

    /** 역할 하나 (id가 null이면 새 역할) */
    private Map<String, Object> role(Long id, RoleCode code, int headcount) {
        Map<String, Object> role = new HashMap<>();
        role.put("id", id);
        role.put("roleCode", code);
        role.put("roleName", code.name());
        role.put("headcount", headcount);
        return role;
    }

    /** 수정 본문 (온라인 프로젝트) */
    private Map<String, Object> body(String title, String contactLink, List<Map<String, Object>> roles) {
        LocalDate deadline = LocalDate.now().plusDays(10);
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("category", "PROJECT");
        body.put("description", "아이디어는 정해져 있고 디자인과 개발을 맡아 줄 분을 찾습니다.");
        body.put("periodStart", deadline.plusDays(3));
        body.put("periodEnd", deadline.plusDays(30));
        body.put("deadline", deadline);
        body.put("meetingType", "ONLINE");
        body.put("roles", roles);
        body.put("contactLink", contactLink);
        return body;
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions update(String token, Long postId, Map<String, Object> body) throws Exception {
        return as(token, put("/api/posts/" + postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
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

    // ===== tests =====

    @Test
    @DisplayName("팀장이 수정하면 200, 기존 역할 수정·새 역할 추가·빠진 역할 삭제, 응답은 상세 모양")
    void updates() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        PostRole video = saveRole(post, RoleCode.VIDEO, 1);

        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(design.getId(), RoleCode.DESIGN, 3),
                        role(null, RoleCode.DEV, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("수정한 모집글 제목"))
                .andExpect(jsonPath("$.data.isMine").value(true))
                .andExpect(jsonPath("$.data.roles", hasSize(2)))
                .andExpect(jsonPath("$.data.roles[0].id").value(design.getId()))
                .andExpect(jsonPath("$.data.roles[0].headcount").value(3))
                .andExpect(jsonPath("$.data.roles[1].roleCode").value("DEV"));

        assertThat(postRoleRepository.findById(video.getId())).isEmpty();
    }

    @Test
    @DisplayName("지원자가 있는 역할은 삭제·인원 감소 409 ROLE_HAS_APPLICANTS, 늘리기는 가능")
    void roleHasApplicants() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        applyOk(aliceToken, post.getId(), design.getId());

        // 삭제 (DESIGN을 빼고 보냄)
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(null, RoleCode.DEV, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_HAS_APPLICANTS"));

        // 인원 감소
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(design.getId(), RoleCode.DESIGN, 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_HAS_APPLICANTS"));

        // 인원 증가
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(design.getId(), RoleCode.DESIGN, 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].headcount").value(4));
    }

    @Test
    @DisplayName("지원자가 있는 역할은 역할 종류 변경 409, 이름·요구 조건 변경은 가능")
    void roleCodeChangeBlocked() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        applyOk(aliceToken, post.getId(), design.getId());

        // 디자인 → 개발로 종류 변경: 막힘
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                role(design.getId(), RoleCode.DEV, 2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_HAS_APPLICANTS"));

        // 종류는 그대로, 이름·요구 조건만 변경: 가능
        Map<String, Object> renamed = role(design.getId(), RoleCode.DESIGN, 2);
        renamed.put("roleName", "UI 디자인");
        renamed.put("requirements", "피그마 가능자");
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].roleCode").value("DESIGN"))
                .andExpect(jsonPath("$.data.roles[0].roleName").value("UI 디자인"))
                .andExpect(jsonPath("$.data.roles[0].requirements").value("피그마 가능자"));
    }

    @Test
    @DisplayName("꽉 차서 마감된 역할은 인원을 늘리면 다시 OPEN")
    void reopensRole() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 1);
        PostRole dev = saveRole(post, RoleCode.DEV, 1);   // 글이 자동 마감되지 않게 하나 더
        Long applicationId = applyOk(aliceToken, post.getId(), design.getId());
        as(leaderToken, patch("/api/applications/" + applicationId + "/accept")).andExpect(status().isOk());
        assertThat(postRoleRepository.findById(design.getId()).orElseThrow().getStatus())
                .isEqualTo(RecruitStatus.CLOSED);

        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(design.getId(), RoleCode.DESIGN, 2),
                        role(dev.getId(), RoleCode.DEV, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.roles[0].filledCount").value(1));
    }

    @Test
    @DisplayName("연락 링크가 바뀌면 수락된 팀원에게 CONTACT_CHANGED 알림")
    void contactChanged() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        Long applicationId = applyOk(aliceToken, post.getId(), design.getId());
        as(leaderToken, patch("/api/applications/" + applicationId + "/accept")).andExpect(status().isOk());
        notificationRepository.deleteAll();

        update(leaderToken, post.getId(), body("수정한 모집글 제목", "https://open.kakao.com/o/new", List.of(
                        role(design.getId(), RoleCode.DESIGN, 2))))
                .andExpect(status().isOk());

        as(aliceToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("CONTACT_CHANGED"));
    }

    @Test
    @DisplayName("공모전으로 바꾸면 행사 정보 저장, 프로젝트로 되돌리면 삭제")
    void event() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        Map<String, Object> contest = body("공모전으로 바꾼 모집글", CONTACT, List.of(
                role(design.getId(), RoleCode.DESIGN, 2)));
        contest.put("category", "CONTEST");
        contest.put("event", Map.of("name", "천안 창업 아이디어 공모전", "host", "천안시",
                "officialUrl", "https://example.com/contest", "applyDeadline", LocalDate.now().plusDays(20)));

        update(leaderToken, post.getId(), contest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.name").value("천안 창업 아이디어 공모전"));
        assertThat(eventRepository.findByPostId(post.getId())).isPresent();

        update(leaderToken, post.getId(), body("프로젝트로 되돌린 모집글", CONTACT, List.of(
                        role(design.getId(), RoleCode.DESIGN, 2))))
                .andExpect(status().isOk());
        assertThat(eventRepository.findByPostId(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("팀장이 아니면 403, 남의 역할 id는 400, 없는 글은 404, 비회원은 401")
    void errors() throws Exception {
        Post post = savePost();
        PostRole design = saveRole(post, RoleCode.DESIGN, 2);
        Map<String, Object> ok = body("수정한 모집글 제목", CONTACT, List.of(role(design.getId(), RoleCode.DESIGN, 2)));

        update(aliceToken, post.getId(), ok)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        update(null, post.getId(), ok).andExpect(status().isUnauthorized());
        update(leaderToken, 999999L, ok).andExpect(status().isNotFound());
        update(leaderToken, post.getId(), body("수정한 모집글 제목", CONTACT, List.of(
                        role(999999L, RoleCode.DESIGN, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
