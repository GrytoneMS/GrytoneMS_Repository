package com.grytonems.backend.domain.profile;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;

/** 프로필 1단계 통합 테스트 (PROF-01, 02, 03, 12) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileApiTest {

    private static final String PROFILE = "/api/users/me/profile";
    private static final String ROLE_TAGS = "/api/users/me/role-tags";
    private static final String SKILLS = "/api/users/me/skills";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired UserRoleTagRepository roleTagRepository;
    @Autowired UserSkillRepository skillRepository;

    String token;
    Long userId;

    @BeforeEach
    void setUp() throws Exception {
        token = signup("alice@example.com", "앨리스");
        userId = userRepository.findByEmail("alice@example.com").orElseThrow().getId();
    }

    @AfterEach
    void cleanUp() {
        roleTagRepository.deleteAll();
        skillRepository.deleteAll();
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
        return objectMapper.readTree(json).at("/data/accessToken").asText();
    }

    private ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return putJson(url, objectMapper.writeValueAsString(body));
    }

    private ResultActions getWith(String token, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // ===== 내 프로필 =====

    @Test
    @DisplayName("가입 직후 내 프로필은 닉네임만 있고 나머지는 비어 있다")
    void initialProfile() throws Exception {
        getWith(token, PROFILE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.nickname").value("앨리스"))
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.school").doesNotExist())
                .andExpect(jsonPath("$.data.major").doesNotExist())
                .andExpect(jsonPath("$.data.grade").doesNotExist())
                .andExpect(jsonPath("$.data.roleTags", hasSize(0)))
                .andExpect(jsonPath("$.data.skills", hasSize(0)));
    }

    // ===== PROF-01 기본 정보 =====

    @Test
    @DisplayName("학과·학년·한 줄 소개를 수정하면 수정된 프로필을 돌려준다")
    void updateBasicInfo() throws Exception {
        putJson(PROFILE, Map.of("major", " 시각디자인학과 ", "grade", 3, "bio", "피그마로 UI 만드는 걸 좋아해요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.major").value("시각디자인학과"))
                .andExpect(jsonPath("$.data.grade").value(3))
                .andExpect(jsonPath("$.data.bio").value("피그마로 UI 만드는 걸 좋아해요"));

        getWith(token, PROFILE)
                .andExpect(jsonPath("$.data.major").value("시각디자인학과"))
                .andExpect(jsonPath("$.data.grade").value(3));

        // 비워서 보내면 지워짐
        putJson(PROFILE, "{\"major\":\"시각디자인학과\",\"bio\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.major").value("시각디자인학과"))
                .andExpect(jsonPath("$.data.grade").doesNotExist())
                .andExpect(jsonPath("$.data.bio").doesNotExist());
    }

    @Test
    @DisplayName("학년이 1~6 밖이거나 소개가 50자를 넘으면 400과 필드 오류")
    void invalidBasicInfo() throws Exception {
        putJson(PROFILE, Map.of("grade", 7))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("grade"));
        putJson(PROFILE, Map.of("bio", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bio"));
        putJson(PROFILE, Map.of("bio", "가".repeat(50)))
                .andExpect(status().isOk());
    }

    // ===== PROF-02 희망 역할 태그 =====

    @Test
    @DisplayName("희망 역할을 통째로 바꿀 수 있고, 중복은 하나로 합쳐진다")
    void updateRoleTags() throws Exception {
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("DESIGN", "DEV", "DESIGN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleTags", contains("DESIGN", "DEV")));

        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("PLANNING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleTags", contains("PLANNING")));

        // 같은 태그를 다시 보내도 문제없음
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("PLANNING", "VIDEO", "MARKETING")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleTags", contains("PLANNING", "VIDEO", "MARKETING")));

        putJson(ROLE_TAGS, Map.of("roleCodes", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleTags", hasSize(0)));
    }

    @Test
    @DisplayName("희망 역할이 3개를 넘거나 없는 코드면 400, 기존 태그는 그대로")
    void invalidRoleTags() throws Exception {
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("DESIGN"))).andExpect(status().isOk());

        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("PLANNING", "DESIGN", "DEV", "VIDEO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("HACKER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putJson(ROLE_TAGS, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("roleCodes"));

        getWith(token, PROFILE).andExpect(jsonPath("$.data.roleTags", contains("DESIGN")));
    }

    // ===== PROF-03 보유 기술 =====

    @Test
    @DisplayName("기술은 공백을 떼고 대소문자만 다른 중복을 합쳐 저장한다")
    void updateSkills() throws Exception {
        putJson(SKILLS, Map.of("skills", List.of(" Figma ", "figma", "Premiere Pro", "Python")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills", contains("Figma", "Premiere Pro", "Python")));

        putJson(SKILLS, Map.of("skills", List.of("Python", "Java")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills", contains("Python", "Java")));

        putJson(SKILLS, Map.of("skills", List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills", hasSize(0)));
    }

    @Test
    @DisplayName("기술은 10개까지, 11개째는 400. 빈 이름·30자 초과도 400")
    void invalidSkills() throws Exception {
        List<String> ten = IntStream.rangeClosed(1, 10).mapToObj(i -> "기술" + i).toList();
        putJson(SKILLS, Map.of("skills", ten))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills", hasSize(10)));

        List<String> eleven = IntStream.rangeClosed(1, 11).mapToObj(i -> "기술" + i).toList();
        putJson(SKILLS, Map.of("skills", eleven))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putJson(SKILLS, Map.of("skills", List.of("Figma", "  ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        putJson(SKILLS, Map.of("skills", List.of("a".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // 실패한 요청은 기존 목록을 바꾸지 않음
        getWith(token, PROFILE).andExpect(jsonPath("$.data.skills", hasSize(10)));
    }

    // ===== PROF-12 타인 프로필 =====

    @Test
    @DisplayName("다른 사람의 프로필을 볼 수 있고, 학교 인증 배지와 학교가 보인다")
    void otherProfile() throws Exception {
        putJson(PROFILE, Map.of("major", "시각디자인학과", "grade", 3, "bio", "안녕하세요"));
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("DESIGN")));
        putJson(SKILLS, Map.of("skills", List.of("Figma")));
        School school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        User alice = userRepository.findById(userId).orElseThrow();
        alice.verify(school, "alice@dankook.ac.kr", LocalDateTime.now());
        userRepository.save(alice);

        String viewer = signup("viewer@example.com", "구경꾼");
        getWith(viewer, "/api/users/" + userId + "/profile")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("앨리스"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.school.name").value("단국대학교"))
                .andExpect(jsonPath("$.data.major").value("시각디자인학과"))
                .andExpect(jsonPath("$.data.grade").value(3))
                .andExpect(jsonPath("$.data.roleTags", contains("DESIGN")))
                .andExpect(jsonPath("$.data.skills", contains("Figma")))
                // 로그인 이메일·학교 이메일 같은 개인정보는 내려가지 않음
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.schoolEmail").doesNotExist());
    }

    @Test
    @DisplayName("없는 사용자는 404, 비회원은 401, 숫자가 아닌 id는 400")
    void otherProfileErrors() throws Exception {
        getWith(token, "/api/users/999999/profile")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/users/" + userId + "/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROFILE)).andExpect(status().isUnauthorized());
        getWith(token, "/api/users/abc/profile")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("다른 사람의 프로필은 내 수정 요청에 영향받지 않는다")
    void updatesAreScopedToMe() throws Exception {
        String other = signup("bob@example.com", "밥이");
        Long otherId = userRepository.findByEmail("bob@example.com").orElseThrow().getId();
        putJson(SKILLS, Map.of("skills", List.of("Figma")));
        putJson(ROLE_TAGS, Map.of("roleCodes", List.of("DESIGN")));

        getWith(other, PROFILE)
                .andExpect(jsonPath("$.data.id").value(otherId))
                .andExpect(jsonPath("$.data.skills", hasSize(0)))
                .andExpect(jsonPath("$.data.roleTags", hasSize(0)));
    }
}
