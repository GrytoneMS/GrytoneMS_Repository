package com.grytonems.backend.domain.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.domain.verification.dto.SchoolEmailConfirmRequest;
import com.grytonems.backend.domain.verification.dto.SchoolEmailSendRequest;
import com.grytonems.backend.domain.verification.dto.SchoolEmailSendResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.mail.EmailSender;

import lombok.RequiredArgsConstructor;

/**
 * AUTH-06 학교 이메일 인증.
 * 학교 선택 → 학교 이메일 입력 → 6자리 코드 메일 발송 → 코드 입력 → 인증 회원(VERIFIED)
 *
 * - 이메일 도메인은 학교별 등록값(schools.email_domain)과 일치해야 함
 * - 코드 유효 5분, 하루 5회까지 발송
 * - 학교 이메일 1개 = 계정 1개
 * - 코드를 5번 틀리면 그 코드는 무효 (무작위 대입 방지)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolVerificationService {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final int MAX_SEND_PER_DAY = 5;
    public static final int MAX_CODE_FAIL = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolVerificationRepository verificationRepository;
    private final VerificationCodeStore codeStore;
    private final EmailSender emailSender;

    /** 인증 코드 메일 발송 */
    public SchoolEmailSendResponse sendCode(Long userId, SchoolEmailSendRequest request) {
        getActiveUser(userId);
        School school = getActiveSchool(request.schoolId());
        String schoolEmail = request.schoolEmail().trim().toLowerCase(Locale.ROOT);

        String domain = school.getEmailDomain().toLowerCase(Locale.ROOT);
        if (!schoolEmail.endsWith("@" + domain)) {
            throw new BusinessException(ErrorCode.SCHOOL_EMAIL_DOMAIN_MISMATCH,
                    school.getName() + " 이메일(@" + domain + ")로 입력해 주세요");
        }
        if (userRepository.existsBySchoolEmailAndIdNot(schoolEmail, userId)) {
            throw new BusinessException(ErrorCode.SCHOOL_EMAIL_IN_USE);
        }

        LocalDate today = LocalDate.now();
        int sentToday = codeStore.getSendCount(userId, today);
        if (sentToday >= MAX_SEND_PER_DAY) {
            throw new BusinessException(ErrorCode.VERIFICATION_SEND_LIMIT);
        }

        String code = newCode();
        // 메일이 실제로 나간 다음에만 코드를 저장하고 횟수를 셉니다
        emailSender.send(schoolEmail, "[천안·아산 팀원 모집] 학교 인증 코드", mailBody(code));
        codeStore.save(userId, new PendingVerification(school.getId(), schoolEmail, code, 0), CODE_TTL);
        codeStore.increaseSendCount(userId, today);

        return new SchoolEmailSendResponse(schoolEmail, (int) CODE_TTL.toSeconds(),
                MAX_SEND_PER_DAY - sentToday - 1);
    }

    /** 코드 확인 → 성공하면 인증 회원으로 바뀐 내 정보를 돌려줍니다 */
    @Transactional
    public UserResponse confirm(Long userId, SchoolEmailConfirmRequest request) {
        User user = getActiveUser(userId);
        PendingVerification pending = codeStore.find(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED));

        if (!sameCode(pending.code(), request.code())) {
            int failCount = codeStore.increaseFailCount(userId);
            if (failCount >= MAX_CODE_FAIL) {
                codeStore.delete(userId);
                throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED,
                        "인증 코드를 " + MAX_CODE_FAIL + "번 틀려 코드가 무효가 됐어요. 코드를 다시 받아 주세요");
            }
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH,
                    "인증 코드가 맞지 않아요 (" + (MAX_CODE_FAIL - failCount) + "번 더 시도할 수 있어요)");
        }

        School school = getActiveSchool(pending.schoolId());
        // 코드를 받은 뒤 다른 계정이 먼저 인증했을 수 있으므로 한 번 더 확인
        if (userRepository.existsBySchoolEmailAndIdNot(pending.schoolEmail(), userId)) {
            codeStore.delete(userId);
            throw new BusinessException(ErrorCode.SCHOOL_EMAIL_IN_USE);
        }

        LocalDateTime now = LocalDateTime.now();
        user.verify(school, pending.schoolEmail(), now);
        verificationRepository.save(SchoolVerification.emailApproved(user, school, Semester.of(now.toLocalDate())));
        codeStore.delete(userId);
        return UserResponse.from(user);
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SUSPENDED);
        }
        return user;
    }

    private School getActiveSchool(Long schoolId) {
        return schoolRepository.findById(schoolId)
                .filter(School::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_NOT_FOUND));
    }

    private static String newCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** 시간 차이로 코드를 추측하지 못하게 고정 시간 비교 */
    private static boolean sameCode(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String mailBody(String code) {
        return """
                학교 인증 코드: %s

                5분 안에 앱 화면에 입력해 주세요.
                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.
                """.formatted(code);
    }
}
