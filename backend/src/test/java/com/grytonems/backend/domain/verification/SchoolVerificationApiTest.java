package com.grytonems.backend.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grytonems.backend.domain.school.Region;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.verification.VerificationTestConfig.InMemoryCodeStore;
import com.grytonems.backend.domain.verification.VerificationTestConfig.RecordingEmailSender;

/** AUTH-06 학교 이메일 인증 통합 테스트 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VerificationTestConfig.class)
class SchoolVerificationApiTest {

    private static final String SEND_URL = "/api/verifications/school-email";
    private static final String CONFIRM_URL = "/api/verifications/school-email/confirm";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    SchoolRepository schoolRepository;

    @Autowired
    SchoolVerificationRepository verificationRepository;

    @Autowired
    InMemoryCodeStore codeStore;

    @Autowired
    RecordingEmailSender mailSender;

    School dankook;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        dankook = schoolRepository.save(School.builder()
                .name("단국대학교").campus("천안캠퍼스").emailDomain("dankook.ac.kr").region(Region.CHEONAN)
                .build());
        token = signup("hyunchan@example.com", "현찬");
    }

    @AfterEach
    void cleanUp() {
        verificationRepository.deleteAll();
        userRepository.deleteAll();
        schoolRepository.deleteAll();
        codeStore.clear();
        mailSender.clear();
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

    private ResultActions send(String token, Long schoolId, String schoolEmail) throws Exception {
        return mockMvc.perform(post(SEND_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("schoolId", schoolId, "schoolEmail", schoolEmail))));
    }

    private ResultActions confirm(String token, String code) throws Exception {
        return mockMvc.perform(post(CONFIRM_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code))));
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    /** 실제 코드와 다른 6자리 숫자 */
    private String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    // ===== tests =====

    @Test
    @DisplayName("코드를 받고 맞게 입력하면 인증 회원이 되고, 글쓰기 권한 검사를 통과한다")
    void sendAndConfirm() throws Exception {
        send(token, dankook.getId(), "Student@Dankook.ac.kr")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schoolEmail").value("student@dankook.ac.kr"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.data.remainingSendCount").value(4));

        assertThat(mailSender.last().to()).isEqualTo("student@dankook.ac.kr");
        String code = mailSender.lastCode();

        confirm(token, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.school.name").value("단국대학교"));

        User user = userRepository.findByEmail("hyunchan@example.com").orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.VERIFIED);
        assertThat(user.getSchoolEmail()).isEqualTo("student@dankook.ac.kr");
        assertThat(user.getVerifiedAt()).isNotNull();
        assertThat(verificationRepository.findAll())
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.getMethod()).isEqualTo(VerificationMethod.EMAIL);
                    assertThat(v.getStatus()).isEqualTo(VerificationStatus.APPROVED);
                    assertThat(v.getSemester()).matches("\\d{4}-[12]");
                });

        // 같은 코드로 다시 확인할 수 없음 (한 번 쓰면 삭제)
        confirm(token, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));

        // 인증 회원은 글쓰기 권한 검사(403 UNVERIFIED)를 통과
        int writeStatus = mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(writeStatus).isNotIn(401, 403);
    }

    @Test
    @DisplayName("선택한 학교 도메인이 아니면 400 SCHOOL_EMAIL_DOMAIN_MISMATCH, 메일은 안 보낸다")
    void domainMismatch() throws Exception {
        send(token, dankook.getId(), "student@gmail.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_DOMAIN_MISMATCH"));
        // 하위 도메인 흉내도 막음
        send(token, dankook.getId(), "student@fake-dankook.ac.kr")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_DOMAIN_MISMATCH"));

        assertThat(mailSender.count()).isZero();
    }

    @Test
    @DisplayName("없는 학교나 비활성 학교면 404 SCHOOL_NOT_FOUND")
    void schoolNotFound() throws Exception {
        send(token, 999_999L, "student@dankook.ac.kr")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));

        dankook.deactivate();
        schoolRepository.save(dankook);
        send(token, dankook.getId(), "student@dankook.ac.kr")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHOOL_NOT_FOUND"));
    }

    @Test
    @DisplayName("입력 형식이 틀리면 400 INVALID_INPUT")
    void invalidInput() throws Exception {
        send(token, dankook.getId(), "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("schoolEmail"));
        confirm(token, "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("code"));
    }

    @Test
    @DisplayName("틀린 코드는 400 VERIFICATION_CODE_MISMATCH, 5번 틀리면 코드가 무효가 된다")
    void wrongCodeFiveTimes() throws Exception {
        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        String code = mailSender.lastCode();

        for (int i = 1; i < SchoolVerificationService.MAX_CODE_FAIL; i++) {
            confirm(token, wrongCode(code))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        }
        confirm(token, wrongCode(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));

        // 이제 맞는 코드도 안 됨
        confirm(token, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
        assertThat(userRepository.findByEmail("hyunchan@example.com").orElseThrow().getStatus())
                .isEqualTo(UserStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("5분이 지나 만료됐거나 코드를 받은 적이 없으면 400 VERIFICATION_CODE_EXPIRED")
    void expiredOrNeverSent() throws Exception {
        confirm(token, "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));

        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        String code = mailSender.lastCode();
        codeStore.expire(userId("hyunchan@example.com"));

        confirm(token, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("새 코드를 받으면 이전 코드는 무효")
    void resendInvalidatesOldCode() throws Exception {
        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        String first = mailSender.lastCode();
        String second;
        do { // 우연히 같은 코드가 나오면 다시 받음
            send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
            second = mailSender.lastCode();
        } while (second.equals(first));

        confirm(token, first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        confirm(token, second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("하루 5번까지 받을 수 있고 6번째는 429 VERIFICATION_SEND_LIMIT")
    void dailySendLimit() throws Exception {
        for (int i = 1; i <= SchoolVerificationService.MAX_SEND_PER_DAY; i++) {
            send(token, dankook.getId(), "student@dankook.ac.kr")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.remainingSendCount")
                            .value(SchoolVerificationService.MAX_SEND_PER_DAY - i));
        }
        send(token, dankook.getId(), "student@dankook.ac.kr")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_SEND_LIMIT"));
        assertThat(mailSender.count()).isEqualTo(SchoolVerificationService.MAX_SEND_PER_DAY);
    }

    @Test
    @DisplayName("다른 계정이 인증한 학교 이메일은 409 SCHOOL_EMAIL_IN_USE")
    void schoolEmailInUse() throws Exception {
        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        confirm(token, mailSender.lastCode()).andExpect(status().isOk());

        String other = signup("other@example.com", "다른사람");
        send(other, dankook.getId(), "student@dankook.ac.kr")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_IN_USE"));

        // 본인은 같은 학교 이메일로 다시 인증(학기 재인증) 가능
        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
    }

    @Test
    @DisplayName("코드를 받은 뒤 다른 계정이 먼저 인증하면 확인 단계에서 409")
    void schoolEmailTakenWhileWaiting() throws Exception {
        String other = signup("other@example.com", "다른사람");

        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        String myCode = mailSender.lastCode();
        send(other, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        confirm(other, mailSender.lastCode()).andExpect(status().isOk());

        confirm(token, myCode)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHOOL_EMAIL_IN_USE"));
    }

    @Test
    @DisplayName("정지 회원은 403 SUSPENDED, 비회원은 401 UNAUTHORIZED")
    void suspendedOrAnonymous() throws Exception {
        User user = userRepository.findByEmail("hyunchan@example.com").orElseThrow();
        user.suspend();
        userRepository.save(user);

        send(token, dankook.getId(), "student@dankook.ac.kr")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUSPENDED"));

        mockMvc.perform(post(SEND_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\":1,\"schoolEmail\":\"a@dankook.ac.kr\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증 후 내 정보에도 학교가 보인다")
    void meAfterVerification() throws Exception {
        send(token, dankook.getId(), "student@dankook.ac.kr").andExpect(status().isOk());
        confirm(token, mailSender.lastCode()).andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.school.id").value(dankook.getId()))
                .andExpect(jsonPath("$.data.school.campus").value("천안캠퍼스"));
    }
}
