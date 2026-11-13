package com.grytonems.backend.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.notification.NotificationRepository;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserRole;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.file.FileStorage;

/** 서류 인증(AUTH-07), 내 인증 상태, 관리자 심사(ADMN-03), 학교 수정(ADMN-05), 재인증 기한 계산(AUTH-08) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentVerificationApiTest {

    private static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9, 9, 9 };

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired SchoolRepository schoolRepository;
    @Autowired SchoolVerificationRepository verificationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired FileStorage fileStorage;

    School school;
    School other;
    String userToken;
    String adminToken;
    Long userId;

    @BeforeEach
    void setUp() throws Exception {
        school = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN).build());
        other = schoolRepository.save(School.builder()
                .name("호서대학교").campus("아산캠퍼스").emailDomain("hoseo.edu").region(Region.ASAN).build());
        userToken = signup("sujin@example.com", "수진");
        userId = userRepository.findByEmail("sujin@example.com").orElseThrow().getId();
        adminToken = signup("admin@example.com", "관리자");
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        userRepository.save(admin);
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        verificationRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
    }

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

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private ResultActions submit(String token, Long schoolId, byte[] bytes) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "card.jpg", "image/jpeg", bytes);
        return as(token, multipart("/api/verifications/document").file(file).param("schoolId", String.valueOf(schoolId)));
    }

    private Long submitOk() throws Exception {
        String json = submit(userToken, school.getId(), JPG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).at("/data/verificationId").asLong();
    }

    @Test
    @DisplayName("서류 신청 → 관리자 목록·사진 → 승인하면 인증 회원, 사진 삭제, VERIFY_RESULT")
    void submitAndApprove() throws Exception {
        as(userToken, get("/api/verifications/me")).andExpect(jsonPath("$.data").value(nullValue()));

        Long id = submitOk();
        String path = verificationRepository.findById(id).orElseThrow().getDocumentUrl();
        assertThat(path).startsWith("documents/");
        assertThat(Files.exists(fileStorage.root().resolve(path))).isTrue();

        // 심사 중에는 다시 신청 못 함, 공개 주소로는 열리지 않음
        submit(userToken, school.getId(), JPG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_PENDING"));
        mockMvc.perform(get("/files/" + path)).andExpect(status().isNotFound());

        as(userToken, get("/api/verifications/me"))
                .andExpect(jsonPath("$.data.method").value("DOCUMENT"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.school.name").value("단국대학교"));

        // 관리자만
        as(userToken, get("/api/admin/verifications")).andExpect(status().isForbidden());
        as(adminToken, get("/api/admin/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].user.nickname").value("수진"))
                .andExpect(jsonPath("$.data.items[0].documentUrl").value("/api/admin/verifications/" + id + "/document"));
        as(userToken, get("/api/admin/verifications/" + id + "/document")).andExpect(status().isForbidden());
        as(adminToken, get("/api/admin/verifications/" + id + "/document"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPG));

        as(adminToken, patch("/api/admin/verifications/" + id + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.documentUrl").value(nullValue()));
        assertThat(Files.exists(fileStorage.root().resolve(path))).isFalse();

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.VERIFIED);
        assertThat(user.getSchool().getId()).isEqualTo(school.getId());
        as(userToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].type").value("VERIFY_RESULT"))
                .andExpect(jsonPath("$.data.items[0].message").value("학교 인증이 완료됐어요"));

        as(adminToken, patch("/api/admin/verifications/" + id + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERIFICATION_ALREADY_REVIEWED"));
        as(adminToken, get("/api/admin/verifications/" + id + "/document")).andExpect(status().isNotFound());
        as(adminToken, get("/api/admin/verifications?status=APPROVED")).andExpect(jsonPath("$.data.items", hasSize(1)));
        as(adminToken, get("/api/admin/verifications")).andExpect(jsonPath("$.data.items", hasSize(0)));

        // 이미 인증 회원
        submit(userToken, school.getId(), JPG)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_VERIFIED"));
    }

    @Test
    @DisplayName("반려: 사유 필수, 미인증 그대로, 사유가 담긴 알림, 다시 신청 가능")
    void reject() throws Exception {
        Long id = submitOk();
        as(adminToken, patch("/api/admin/verifications/" + id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        as(adminToken, patch("/api/admin/verifications/" + id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "사진이 흐려서 학번이 보이지 않아요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("사진이 흐려서 학번이 보이지 않아요"));

        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(UserStatus.UNVERIFIED);
        as(userToken, get("/api/notifications"))
                .andExpect(jsonPath("$.data.items[0].message").value("학교 인증이 반려됐어요: 사진이 흐려서 학번이 보이지 않아요"));
        as(userToken, get("/api/verifications/me"))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("사진이 흐려서 학번이 보이지 않아요"));
        submitOk();
    }

    @Test
    @DisplayName("신청 오류: 형식·크기·학교 없음·비활성 학교·파일 없음")
    void submitErrors() throws Exception {
        submit(userToken, school.getId(), "not an image".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        byte[] big = new byte[(int) FileStorage.MAX_IMAGE_BYTES + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        submit(userToken, school.getId(), big).andExpect(status().isBadRequest());
        submit(userToken, 999999L, JPG)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
        as(userToken, multipart("/api/verifications/document").param("schoolId", String.valueOf(school.getId())))
                .andExpect(status().isBadRequest());
        submit(null, school.getId(), JPG).andExpect(status().isUnauthorized());

        // ADMN-05 비활성 학교는 새 인증 불가, 학교 목록에서도 빠짐
        as(adminToken, patch("/api/admin/schools/" + other.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.name").value("호서대학교"));
        submit(userToken, other.getId(), JPG).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/schools")).andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("학교 수정: 바꿀 값만, 도메인 중복 409, 없는 학교 404, 관리자만")
    void updateSchool() throws Exception {
        as(adminToken, patch("/api/admin/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"campus\":\"죽전캠퍼스\",\"emailDomain\":\"DKU.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("단국대학교"))
                .andExpect(jsonPath("$.data.campus").value("죽전캠퍼스"))
                .andExpect(jsonPath("$.data.emailDomain").value("dku.edu"))
                .andExpect(jsonPath("$.data.active").value(true));
        as(adminToken, patch("/api/admin/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailDomain\":\"hoseo.edu\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_DOMAIN_DUPLICATED"));
        as(adminToken, patch("/api/admin/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailDomain\":\"not a domain\"}"))
                .andExpect(status().isBadRequest());
        as(adminToken, patch("/api/admin/schools/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isNotFound());
        as(userToken, patch("/api/admin/schools/" + school.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("재인증 기한: 학기 시작 전 인증한 회원만 시작일부터 14일 동안 기한이 보임")
    void reverifyPolicy() {
        assertThat(ReverifyPolicy.semesterStart(LocalDate.of(2027, 3, 5))).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(ReverifyPolicy.semesterStart(LocalDate.of(2027, 2, 5))).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ReverifyPolicy.semesterStart(LocalDate.of(2026, 11, 13))).isEqualTo(LocalDate.of(2026, 9, 1));

        User user = userRepository.findById(userId).orElseThrow();
        user.verify(school, "sujin@dankook.ac.kr", LocalDateTime.of(2026, 12, 1, 10, 0));
        assertThat(ReverifyPolicy.dueDateOf(user, LocalDate.of(2027, 3, 1))).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(ReverifyPolicy.dueDateOf(user, LocalDate.of(2027, 3, 14))).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(ReverifyPolicy.dueDateOf(user, LocalDate.of(2027, 3, 15))).isNull();
        assertThat(ReverifyPolicy.dueDateOf(user, LocalDate.of(2027, 2, 28))).isNull();

        user.verify(school, "sujin@dankook.ac.kr", LocalDateTime.of(2027, 3, 2, 10, 0));
        assertThat(ReverifyPolicy.dueDateOf(user, LocalDate.of(2027, 3, 5))).isNull();
    }
}
