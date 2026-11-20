package com.grytonems.backend.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
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

/** 프로필 2단계: 이미지(PROF-01), 이력 항목(PROF-04~07, 09), 활동 이력(PROF-08), 완성도(PROF-10), 지원자 프로필(APPL-02) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileItemApiTest {

    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };
    private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3 };

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ProfileItemRepository itemRepository;
    @Autowired UserRoleTagRepository roleTagRepository;
    @Autowired UserSkillRepository skillRepository;

    School school;
    String aliceToken;
    String bobToken;
    Long aliceId;
    Long bobId;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        aliceToken = signupVerified("alice@example.com", "앨리스");
        bobToken = signupVerified("bob@example.com", "밥이");
        aliceId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();
        bobId = userRepository.findByEmail("bob@example.com").orElseThrow().getId();
    }

    @AfterEach
    void cleanUp() {
        itemRepository.deleteAll();
        roleTagRepository.deleteAll();
        skillRepository.deleteAll();
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

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private Map<String, Object> item(String type, String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("itemType", type);
        body.put("title", title);
        return body;
    }

    private Long addItem(String token, Map<String, Object> body) throws Exception {
        String json = as(token, json(post("/api/users/me/profile-items"), body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/id").asLong();
    }

    private ResultActions upload(String token, byte[] bytes, String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "image/png", bytes);
        return as(token, multipart(HttpMethod.PUT, "/api/users/me/profile-image").file(file));
    }

    // ===== tests =====

    @Test
    @DisplayName("이력 항목: 종류별 필수 칸, 정렬, 수정·삭제, 다른 사람에게는 PUBLIC만")
    void items() throws Exception {
        Map<String, Object> club = item("EXPERIENCE", "단국대 멋쟁이사자처럼");
        club.put("subType", "CLUB");
        club.put("role", "프론트엔드");
        club.put("startDate", "2026-03-01");
        Long clubId = addItem(aliceToken, club);

        Map<String, Object> intern = item("EXPERIENCE", "스타트업 인턴");
        intern.put("subType", "INTERN");
        intern.put("startDate", "2026-07-01");
        intern.put("endDate", "2026-08-31");
        intern.put("visibility", "APPLIED_LEADER");
        addItem(aliceToken, intern);

        Map<String, Object> award = item("AWARD", "충남 공모전");
        award.put("issuer", "우수상");
        award.put("startDate", "2026-05-20");
        award.put("url", "https://ignored.example.com");
        addItem(aliceToken, award);

        Map<String, Object> link = item("PORTFOLIO", "GitHub");
        link.put("url", "https://github.com/alice");
        Long linkId = addItem(aliceToken, link);

        // 필수 칸 오류
        as(aliceToken, json(post("/api/users/me/profile-items"), item("EXPERIENCE", "유형 없음")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        as(aliceToken, json(post("/api/users/me/profile-items"), item("CERTIFICATE", "발급 기관 없음")))
                .andExpect(status().isBadRequest());
        Map<String, Object> badUrl = item("PORTFOLIO", "링크");
        badUrl.put("url", "javascript:alert(1)");
        as(aliceToken, json(post("/api/users/me/profile-items"), badUrl)).andExpect(status().isBadRequest());
        Map<String, Object> badDate = item("EXPERIENCE", "날짜 거꾸로");
        badDate.put("subType", "ETC");
        badDate.put("startDate", "2026-05-01");
        badDate.put("endDate", "2026-04-01");
        as(aliceToken, json(post("/api/users/me/profile-items"), badDate)).andExpect(status().isBadRequest());

        // 내 프로필: 종류 순서, 경력은 시작일 최신순, 쓰지 않는 칸은 null
        as(aliceToken, get("/api/users/me/profile"))
                .andExpect(jsonPath("$.data.items", hasSize(4)))
                .andExpect(jsonPath("$.data.items[0].title").value("스타트업 인턴"))
                .andExpect(jsonPath("$.data.items[1].title").value("단국대 멋쟁이사자처럼"))
                .andExpect(jsonPath("$.data.items[1].endDate").value(nullValue()))
                .andExpect(jsonPath("$.data.items[2].itemType").value("AWARD"))
                .andExpect(jsonPath("$.data.items[2].url").value(nullValue()))
                .andExpect(jsonPath("$.data.items[3].url").value("https://github.com/alice"))
                .andExpect(jsonPath("$.data.showActivity").value(true));

        // 다른 사람에게는 PUBLIC만, showActivity·completeness는 null
        as(bobToken, get("/api/users/" + aliceId + "/profile"))
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.showActivity").value(nullValue()))
                .andExpect(jsonPath("$.data.completeness").value(nullValue()));

        // 수정: 종류는 못 바꿈
        Map<String, Object> edited = item("EXPERIENCE", "멋사 12기");
        edited.put("subType", "CLUB");
        edited.put("startDate", "2026-03-01");
        as(aliceToken, json(put("/api/users/me/profile-items/" + clubId), edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("멋사 12기"))
                .andExpect(jsonPath("$.data.role").value(nullValue()));
        as(aliceToken, json(put("/api/users/me/profile-items/" + clubId), link))
                .andExpect(status().isBadRequest());
        // 남의 항목은 404
        as(bobToken, json(put("/api/users/me/profile-items/" + clubId), edited)).andExpect(status().isNotFound());
        as(bobToken, delete("/api/users/me/profile-items/" + linkId)).andExpect(status().isNotFound());

        as(aliceToken, delete("/api/users/me/profile-items/" + linkId)).andExpect(status().isOk());
        as(aliceToken, get("/api/users/me/profile")).andExpect(jsonPath("$.data.items", hasSize(3)));
        assertThat(itemRepository.findById(linkId).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("포트폴리오는 5개까지")
    void portfolioLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            Map<String, Object> link = item("PORTFOLIO", "링크" + i);
            link.put("url", "https://example.com/" + i);
            addItem(aliceToken, link);
        }
        Map<String, Object> sixth = item("PORTFOLIO", "여섯째");
        sixth.put("url", "https://example.com/6");
        as(aliceToken, json(post("/api/users/me/profile-items"), sixth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("프로필 이미지: png·jpg 저장 후 공개 주소로 열림, 다른 형식 400, 삭제")
    void profileImage() throws Exception {
        String json = upload(aliceToken, PNG, "me.png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl", startsWith("http://localhost/files/profiles/")))
                .andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(json).at("/data/profileImageUrl").asText();
        mockMvc.perform(get(url.replace("http://localhost", ""))).andExpect(status().isOk())
                .andExpect(content().bytes(PNG));

        // 새 이미지로 바꾸면 이전 파일은 지워짐
        upload(aliceToken, JPG, "me.jpg")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl", startsWith("http://localhost/files/profiles/")));
        mockMvc.perform(get(url.replace("http://localhost", ""))).andExpect(status().isNotFound());

        upload(aliceToken, "GIF89a-not-allowed".getBytes(), "me.gif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        upload(aliceToken, new byte[0], "empty.png").andExpect(status().isBadRequest());

        as(aliceToken, get("/api/users/me")).andExpect(jsonPath("$.data.profileImageUrl", startsWith("http://")));
        as(aliceToken, delete("/api/users/me/profile-image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()));
        as(aliceToken, get("/api/users/me")).andExpect(jsonPath("$.data.profileImageUrl").value(nullValue()));
    }

    @Test
    @DisplayName("완성도: 항목마다 점수, 다 채우면 100")
    void completeness() throws Exception {
        as(aliceToken, get("/api/users/me/profile")).andExpect(jsonPath("$.data.completeness").value(0));

        as(aliceToken, json(put("/api/users/me/profile"), Map.of("major", "시각디자인학과", "grade", 3, "bio", "안녕하세요")))
                .andExpect(jsonPath("$.data.completeness").value(30));
        as(aliceToken, json(put("/api/users/me/role-tags"), Map.of("roleCodes", java.util.List.of("DESIGN"))))
                .andExpect(jsonPath("$.data.completeness").value(50));
        as(aliceToken, json(put("/api/users/me/skills"), Map.of("skills", java.util.List.of("Figma"))))
                .andExpect(jsonPath("$.data.completeness").value(60));
        upload(aliceToken, PNG, "me.png").andExpect(status().isOk());

        Map<String, Object> club = item("EXPERIENCE", "동아리");
        club.put("subType", "CLUB");
        club.put("startDate", "2026-03-01");
        addItem(aliceToken, club);
        Map<String, Object> cert = item("CERTIFICATE", "정보처리기사");
        cert.put("issuer", "한국산업인력공단");
        cert.put("startDate", "2026-06-01");
        addItem(aliceToken, cert);
        Map<String, Object> link = item("PORTFOLIO", "Behance");
        link.put("url", "https://behance.net/alice");
        addItem(aliceToken, link);

        as(aliceToken, get("/api/users/me/profile")).andExpect(jsonPath("$.data.completeness").value(100));
        as(aliceToken, delete("/api/users/me/profile-image")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("활동 이력과 노출 설정, 지원자 프로필은 그 글의 팀장만 모든 항목을 봄")
    void activitiesAndApplicantProfile() throws Exception {
        LocalDate deadline = LocalDate.now().plusDays(7);
        Post post = postRepository.save(Post.builder()
                .leaderId(bobId)
                .title("밥의 공모전 팀 모집")
                .category(PostCategory.CONTEST)
                .description("공모전에 함께 나갈 디자이너를 찾습니다. 편하게 지원해 주세요.")
                .periodStart(deadline.plusDays(3))
                .periodEnd(deadline.plusDays(30))
                .deadline(deadline)
                .meetingType(MeetingType.ONLINE)
                .contactLink("https://open.kakao.com/o/test")
                .build());
        PostRole role = postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(2).build());

        Map<String, Object> secret = item("EXPERIENCE", "비공개 인턴");
        secret.put("subType", "INTERN");
        secret.put("startDate", "2026-07-01");
        secret.put("visibility", "APPLIED_LEADER");
        addItem(aliceToken, secret);

        String json = as(aliceToken, json(post("/api/posts/" + post.getId() + "/applications"),
                        Map.of("postRoleId", role.getId(),
                                "message", "피그마로 앱 화면 세 개를 디자인해 본 경험이 있어요. 열심히 하겠습니다.",
                                "availableTime", "평일 저녁")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(json).at("/data/id").asLong();

        // 지원자 프로필: 팀장은 비공개 항목까지, 다른 사람은 403
        as(bobToken, get("/api/applications/" + applicationId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(aliceId))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.completeness").value(nullValue()));
        as(aliceToken, get("/api/applications/" + applicationId + "/profile"))
                .andExpect(status().isForbidden());
        as(bobToken, get("/api/applications/999999/profile")).andExpect(status().isNotFound());
        as(bobToken, get("/api/users/" + aliceId + "/profile")).andExpect(jsonPath("$.data.items", hasSize(0)));

        // 수락되면 활동 이력에 나옴
        as(bobToken, patch("/api/applications/" + applicationId + "/accept")).andExpect(status().isOk());
        as(aliceToken, get("/api/users/me/profile"))
                .andExpect(jsonPath("$.data.activities", hasSize(1)))
                .andExpect(jsonPath("$.data.activities[0].postTitle").value("밥의 공모전 팀 모집"))
                .andExpect(jsonPath("$.data.activities[0].roleName").value("UI 디자인"))
                .andExpect(jsonPath("$.data.activities[0].left").value(false));
        as(bobToken, get("/api/users/" + aliceId + "/profile")).andExpect(jsonPath("$.data.activities", hasSize(1)));

        // 숨기면 다른 사람에게는 빈 목록, 나에게는 그대로
        as(aliceToken, json(put("/api/users/me/activity-visibility"), Map.of("showActivity", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.showActivity").value(false))
                .andExpect(jsonPath("$.data.activities", hasSize(1)));
        as(bobToken, get("/api/users/" + aliceId + "/profile")).andExpect(jsonPath("$.data.activities", hasSize(0)));

        // 중도 하차
        as(aliceToken, patch("/api/applications/" + applicationId + "/leave")).andExpect(status().isOk());
        as(aliceToken, get("/api/users/me/profile")).andExpect(jsonPath("$.data.activities[0].left").value(true));
    }
}
