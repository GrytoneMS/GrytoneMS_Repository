package com.grytonems.backend.domain.scrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import org.springframework.test.util.ReflectionTestUtils;
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
import com.grytonems.backend.domain.post.PostDeadlineScheduler;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/**
 * 스크랩 API 테스트 (SCRP-01 스크랩·해제, SCRP-02 내 스크랩, 목록·상세 북마크 표시, 스크랩 글 마감 D-1 알림)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScrapApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PostDeadlineScheduler scheduler;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired PostRepository postRepository;
    @Autowired PostRoleRepository postRoleRepository;
    @Autowired EventRepository eventRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired ScrapRepository scrapRepository;

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
        leaderToken = signup("leader@example.com", "팀장", true);
        leaderId = userRepository.findByEmail("leader@example.com").orElseThrow().getId();
        aliceToken = signup("alice@example.com", "앨리스", true);
        bobToken = signup("bob@example.com", "밥이", true);
    }

    @AfterEach
    void cleanUp() {
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

    private Post savePost(String title, LocalDate deadline) {
        Post post = postRepository.save(Post.builder()
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
        postRoleRepository.save(PostRole.builder()
                .post(post).roleCode(RoleCode.DESIGN).roleName("UI 디자인").headcount(2).build());
        return post;
    }

    private Post savePost(String title) {
        return savePost(title, LocalDate.now().plusDays(7));
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions scrap(String token, Long postId) throws Exception {
        return as(token, put("/api/posts/" + postId + "/scrap"));
    }

    private ResultActions unscrap(String token, Long postId) throws Exception {
        return as(token, delete("/api/posts/" + postId + "/scrap"));
    }

    // ===== SCRP-01 스크랩·해제 =====

    @Nested
    @DisplayName("스크랩·해제 PUT·DELETE /api/posts/{postId}/scrap")
    class Toggle {

        @Test
        @DisplayName("스크랩하면 isScrapped=true, 두 번 해도 한 번만 저장. 해제하면 false, 두 번 해도 성공")
        void scrapAndUnscrap() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");

            scrap(aliceToken, post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.postId").value(post.getId()))
                    .andExpect(jsonPath("$.data.isScrapped").value(true));
            scrap(aliceToken, post.getId()).andExpect(status().isOk());
            assertThat(scrapRepository.count()).isEqualTo(1);

            unscrap(aliceToken, post.getId())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.isScrapped").value(false));
            unscrap(aliceToken, post.getId()).andExpect(status().isOk());
            assertThat(scrapRepository.count()).isZero();
        }

        @Test
        @DisplayName("본인 글은 400 CANNOT_SCRAP_OWN_POST, 없는 글·숨김 글은 404")
        void rules() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            scrap(leaderToken, post.getId())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_SCRAP_OWN_POST"));
            scrap(aliceToken, 999999L).andExpect(status().isNotFound());

            Post hidden = savePost("숨김 처리된 모집글");
            hidden.hide();
            postRepository.save(hidden);
            scrap(aliceToken, hidden.getId()).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("마감된 글도 스크랩 가능")
        void closedPost() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            as(leaderToken, patch("/api/posts/" + post.getId() + "/close")).andExpect(status().isOk());
            scrap(aliceToken, post.getId()).andExpect(status().isOk());
        }

        @Test
        @DisplayName("미인증 회원은 스크랩 403 UNVERIFIED (해제는 가능), 비회원은 401")
        void auth() throws Exception {
            Post post = savePost("디자이너 구해요 프로젝트");
            String unverified = signup("new@example.com", "새회원", false);

            scrap(unverified, post.getId())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
            unscrap(unverified, post.getId()).andExpect(status().isOk());
            scrap(null, post.getId()).andExpect(status().isUnauthorized());
            unscrap(null, post.getId()).andExpect(status().isUnauthorized());
        }
    }

    // ===== 목록·상세 북마크 표시 =====

    @Nested
    @DisplayName("목록·상세의 isScrapped")
    class Marker {

        @Test
        @DisplayName("내가 스크랩한 글만 true, 다른 사람·비회원에게는 false")
        void listAndDetail() throws Exception {
            Post scrapped = savePost("스크랩한 모집글이에요");
            Post other = savePost("스크랩 안 한 모집글");
            scrap(aliceToken, scrapped.getId()).andExpect(status().isOk());

            as(aliceToken, get("/api/posts"))
                    .andExpect(jsonPath("$.data.items[*].id",
                            contains(other.getId().intValue(), scrapped.getId().intValue())))
                    .andExpect(jsonPath("$.data.items[0].isScrapped").value(false))
                    .andExpect(jsonPath("$.data.items[1].isScrapped").value(true));
            as(bobToken, get("/api/posts")).andExpect(jsonPath("$.data.items[1].isScrapped").value(false));
            as(null, get("/api/posts")).andExpect(jsonPath("$.data.items[1].isScrapped").value(false));

            as(aliceToken, get("/api/posts/" + scrapped.getId())).andExpect(jsonPath("$.data.isScrapped").value(true));
            as(aliceToken, get("/api/posts/" + other.getId())).andExpect(jsonPath("$.data.isScrapped").value(false));
            as(null, get("/api/posts/" + scrapped.getId())).andExpect(jsonPath("$.data.isScrapped").value(false));
        }
    }

    // ===== SCRP-02 내 스크랩 =====

    @Nested
    @DisplayName("내 스크랩 GET /api/users/me/scraps")
    class MyScraps {

        @Test
        @DisplayName("최신 스크랩순, 카드는 목록과 같은 모양이고 마감 상태가 보인다")
        void latestFirst() throws Exception {
            Post first = savePost("먼저 올라온 모집글");
            Post second = savePost("나중에 올라온 모집글");
            scrap(aliceToken, second.getId()).andExpect(status().isOk());
            scrap(aliceToken, first.getId()).andExpect(status().isOk());
            as(leaderToken, patch("/api/posts/" + first.getId() + "/close")).andExpect(status().isOk());

            as(aliceToken, get("/api/users/me/scraps"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].post.id",
                            contains(first.getId().intValue(), second.getId().intValue())))
                    .andExpect(jsonPath("$.data.items[0].scrapId").isNumber())
                    .andExpect(jsonPath("$.data.items[0].scrappedAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.items[0].post.status").value("CLOSED"))
                    .andExpect(jsonPath("$.data.items[0].post.isScrapped").value(true))
                    .andExpect(jsonPath("$.data.items[0].post.leader.nickname").value("팀장"))
                    .andExpect(jsonPath("$.data.items[0].post.roles[0].roleName").value("UI 디자인"))
                    .andExpect(jsonPath("$.data.items[1].post.status").value("OPEN"));
            as(bobToken, get("/api/users/me/scraps")).andExpect(jsonPath("$.data.items", hasSize(0)));
        }

        @Test
        @DisplayName("삭제된 글·숨김 글은 목록에서 자동으로 빠진다")
        void excludesDeletedAndHidden() throws Exception {
            Post kept = savePost("남아 있는 모집글");
            Post deleted = savePost("삭제될 모집글이에요");
            Post hidden = savePost("숨겨질 모집글이에요");
            for (Post post : new Post[] {kept, deleted, hidden}) {
                scrap(aliceToken, post.getId()).andExpect(status().isOk());
            }
            Post d = postRepository.findById(deleted.getId()).orElseThrow();
            ReflectionTestUtils.setField(d, "deletedAt", LocalDateTime.now());
            postRepository.save(d);
            Post h = postRepository.findById(hidden.getId()).orElseThrow();
            h.hide();
            postRepository.save(h);

            as(aliceToken, get("/api/users/me/scraps"))
                    .andExpect(jsonPath("$.data.items[*].post.id", contains(kept.getId().intValue())));
        }

        @Test
        @DisplayName("size와 nextCursor로 페이지 넘김, 비회원은 401")
        void cursor() throws Exception {
            Post a = savePost("첫 번째 모집글이에요");
            Post b = savePost("두 번째 모집글이에요");
            Post c = savePost("세 번째 모집글이에요");
            scrap(aliceToken, a.getId()).andExpect(status().isOk());
            scrap(aliceToken, b.getId()).andExpect(status().isOk());
            scrap(aliceToken, c.getId()).andExpect(status().isOk());

            String json = as(aliceToken, get("/api/users/me/scraps?size=2"))
                    .andExpect(jsonPath("$.data.items[*].post.id", contains(c.getId().intValue(), b.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andReturn().getResponse().getContentAsString();
            String cursor = objectMapper.readTree(json).at("/data/nextCursor").asText();

            as(aliceToken, get("/api/users/me/scraps?size=2&cursor=" + cursor))
                    .andExpect(jsonPath("$.data.items[*].post.id", contains(a.getId().intValue())))
                    .andExpect(jsonPath("$.data.hasNext").value(false));
            as(null, get("/api/users/me/scraps")).andExpect(status().isUnauthorized());
        }
    }

    // ===== 스크랩 글 마감 D-1 알림 =====

    @Test
    @DisplayName("내일 마감인 글을 스크랩한 사람에게 SCRAP_DEADLINE_SOON, 팀장에게는 DEADLINE_SOON")
    void deadlineSoonForScrappers() throws Exception {
        LocalDate today = LocalDate.now();
        Post tomorrow = savePost("내일 마감인 모집글", today.plusDays(1));
        Post later = savePost("모레 마감인 모집글", today.plusDays(2));
        scrap(aliceToken, tomorrow.getId()).andExpect(status().isOk());
        scrap(bobToken, later.getId()).andExpect(status().isOk());

        assertThat(scheduler.notifyDeadlineTomorrowFor(today)).isEqualTo(2);

        as(aliceToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("SCRAP_DEADLINE_SOON"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(tomorrow.getId()))
                .andExpect(jsonPath("$.data.items[0].message").value("스크랩한 '내일 마감인 모집글' 모집이 내일 마감돼요"));
        as(leaderToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].type").value("DEADLINE_SOON"));
        as(bobToken, get("/api/notifications")).andExpect(jsonPath("$.data.items", hasSize(0)));
    }
}
