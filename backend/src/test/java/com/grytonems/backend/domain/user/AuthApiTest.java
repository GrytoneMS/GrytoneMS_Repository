package com.grytonems.backend.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;

/**
 * 회원가입·로그인·권한 통합 테스트.
 * API 명세서(1차)의 응답 형식과 오류 코드대로 동작하는지 확인합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    private static final String PASSWORD = "abcd1234";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SchoolRepository schoolRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

    // ===== helpers =====

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> signupBody(String email, String password, String nickname) {
        return Map.of(
                "email", email,
                "password", password,
                "nickname", nickname,
                "agreeTerms", true,
                "agreePrivacy", true);
    }

    /** 가입하고 토큰을 돌려줍니다 */
    private String signup(String email, String nickname) throws Exception {
        String json = postJson("/api/auth/signup", signupBody(email, PASSWORD, nickname))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private School saveSchool() {
        return schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
    }

    private void makeVerified(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.verify(saveSchool(), "student@dankook.ac.kr", LocalDateTime.now());
        userRepository.save(user);
    }

    private void makeSuspended(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.suspend();
        userRepository.save(user);
    }

    private void makeAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
        userRepository.save(user);
    }

    private static void assertNotBlockedBySecurity(int status) {
        assertThat(status).isNotIn(401, 403);
    }

    // ===== AUTH-01 회원가입 =====

    @Nested
    @DisplayName("회원가입 POST /api/auth/signup")
    class Signup {

        @Test
        @DisplayName("성공하면 201과 토큰, 미인증 상태의 사용자 정보를 준다")
        void success() throws Exception {
            postJson("/api/auth/signup", signupBody("hyunchan@example.com", PASSWORD, "현찬"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.id").isNumber())
                    .andExpect(jsonPath("$.data.user.nickname").value("현찬"))
                    .andExpect(jsonPath("$.data.user.status").value("UNVERIFIED"))
                    .andExpect(jsonPath("$.data.user.role").value("USER"))
                    .andExpect(jsonPath("$.data.user.school").doesNotExist());

            User saved = userRepository.findByEmail("hyunchan@example.com").orElseThrow();
            assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD); // 비밀번호는 해시로 저장
        }

        @Test
        @DisplayName("대소문자만 다른 이메일도 중복으로 막는다 (409 EMAIL_DUPLICATED)")
        void duplicateEmail() throws Exception {
            signup("hyunchan@example.com", "현찬");

            postJson("/api/auth/signup", signupBody("HyunChan@Example.com", PASSWORD, "다른닉네임"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        }

        @Test
        @DisplayName("닉네임이 겹치면 409 NICKNAME_DUPLICATED")
        void duplicateNickname() throws Exception {
            signup("a@example.com", "현찬");

            postJson("/api/auth/signup", signupBody("b@example.com", PASSWORD, "현찬"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        }

        @Test
        @DisplayName("입력 규칙을 어기면 400과 필드별 오류를 준다")
        void invalidInput() throws Exception {
            Map<String, Object> body = Map.of(
                    "email", "not-an-email",
                    "password", "abcdefgh",      // 숫자 없음
                    "nickname", "가",             // 1자
                    "agreeTerms", false,
                    "agreePrivacy", true);

            postJson("/api/auth/signup", body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.fieldErrors[*].field")
                            .value(hasItems("email", "password", "nickname", "agreeTerms")));
        }
    }

    // ===== AUTH-04 로그인 =====

    @Nested
    @DisplayName("로그인 POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("성공하면 토큰을 주고, 그 토큰으로 내 정보를 볼 수 있다")
        void successAndMe() throws Exception {
            signup("hyunchan@example.com", "현찬");

            String json = login("hyunchan@example.com", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nickname").value("현찬"))
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(json).at("/data/accessToken").asText();

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("현찬"))
                    .andExpect(jsonPath("$.data.status").value("UNVERIFIED"));
        }

        @Test
        @DisplayName("비밀번호가 틀리거나 없는 이메일이면 똑같이 401 LOGIN_FAILED")
        void wrongPasswordOrUnknownEmail() throws Exception {
            signup("hyunchan@example.com", "현찬");

            login("hyunchan@example.com", "wrong1234")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
            login("nobody@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        @Test
        @DisplayName("5회 연속 실패하면 잠기고, 맞는 비밀번호도 423 LOGIN_LOCKED")
        void lockAfterFiveFailures() throws Exception {
            signup("hyunchan@example.com", "현찬");

            for (int i = 0; i < User.MAX_LOGIN_FAIL - 1; i++) {
                login("hyunchan@example.com", "wrong1234")
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
            }
            login("hyunchan@example.com", "wrong1234")
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));

            login("hyunchan@example.com", PASSWORD)
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        }

        @Test
        @DisplayName("성공하면 실패 횟수가 초기화된다")
        void successResetsFailCount() throws Exception {
            signup("hyunchan@example.com", "현찬");

            for (int i = 0; i < User.MAX_LOGIN_FAIL - 1; i++) {
                login("hyunchan@example.com", "wrong1234");
            }
            login("hyunchan@example.com", PASSWORD).andExpect(status().isOk());

            // 초기화됐으므로 다시 4번 틀려도 잠기지 않음
            for (int i = 0; i < User.MAX_LOGIN_FAIL - 1; i++) {
                login("hyunchan@example.com", "wrong1234").andExpect(status().isUnauthorized());
            }
            login("hyunchan@example.com", PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("로그아웃은 로그인한 상태에서 200")
        void logout() throws Exception {
            String token = signup("hyunchan@example.com", "현찬");

            mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ===== 권한 =====

    @Nested
    @DisplayName("권한 체크")
    class Authorization {

        @Test
        @DisplayName("토큰 없이 로그인 필요한 API를 부르면 401 UNAUTHORIZED")
        void noToken() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("위조된 토큰도 401 UNAUTHORIZED")
        void invalidToken() throws Exception {
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("비회원도 학교 목록과 모집글 목록·상세는 볼 수 있다")
        void publicEndpoints() throws Exception {
            mockMvc.perform(get("/api/schools"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            assertNotBlockedBySecurity(mockMvc.perform(get("/api/posts")).andReturn().getResponse().getStatus());
            assertNotBlockedBySecurity(mockMvc.perform(get("/api/posts/1")).andReturn().getResponse().getStatus());
        }

        @Test
        @DisplayName("지원자 목록·연락 수단은 상세 주소와 달리 로그인이 필요하다")
        void postSubResourcesNeedLogin() throws Exception {
            mockMvc.perform(get("/api/posts/1/applications"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/posts/1/contact"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("미인증 회원이 글쓰기·지원하면 403 UNVERIFIED")
        void unverifiedCannotWrite() throws Exception {
            String token = signup("hyunchan@example.com", "현찬");

            mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
            mockMvc.perform(post("/api/posts/1/applications").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("UNVERIFIED"));
        }

        @Test
        @DisplayName("학교 인증 회원은 글쓰기 권한 검사를 통과한다")
        void verifiedCanWrite() throws Exception {
            String token = signup("hyunchan@example.com", "현찬");
            makeVerified("hyunchan@example.com");

            int status = mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            assertNotBlockedBySecurity(status);

            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                    .andExpect(jsonPath("$.data.school.name").value("단국대학교"))
                    .andExpect(jsonPath("$.data.school.campus").value("천안캠퍼스"));
        }

        @Test
        @DisplayName("정지 회원이 글쓰기하면 403 SUSPENDED, 내 정보는 볼 수 있다")
        void suspended() throws Exception {
            String token = signup("hyunchan@example.com", "현찬");
            makeVerified("hyunchan@example.com");
            makeSuspended("hyunchan@example.com");

            mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SUSPENDED"));
            mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        }

        @Test
        @DisplayName("관리자 API는 비회원 401, 일반 회원 403 FORBIDDEN, 관리자만 통과")
        void adminOnly() throws Exception {
            Map<String, Object> school = Map.of(
                    "name", "호서대학교", "campus", "아산캠퍼스", "emailDomain", "hoseo.edu", "region", "ASAN");

            postJson("/api/admin/schools", school)
                    .andExpect(status().isUnauthorized());

            String token = signup("hyunchan@example.com", "현찬");
            mockMvc.perform(post("/api/admin/schools").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(school)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));

            makeAdmin("hyunchan@example.com");
            mockMvc.perform(post("/api/admin/schools").header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(school)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.emailDomain").value("hoseo.edu"));
        }
    }

    // ===== CORS·기타 =====

    @Nested
    @DisplayName("CORS·기타")
    class Misc {

        @Test
        @DisplayName("프론트 개발 서버(localhost:5173)의 사전 요청은 허용한다")
        void corsAllowed() throws Exception {
            mockMvc.perform(options("/api/users/me")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        }

        @Test
        @DisplayName("허용하지 않은 주소의 사전 요청은 거부한다")
        void corsRejected() throws Exception {
            mockMvc.perform(options("/api/users/me")
                            .header(HttpHeaders.ORIGIN, "http://evil.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("/api 밖의 없는 주소는 404 (브라우저의 favicon 요청 등)")
        void unknownPathIs404() throws Exception {
            mockMvc.perform(get("/favicon.ico"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }
}
