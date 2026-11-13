package com.grytonems.backend.domain.verification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.school.School;
import com.grytonems.backend.domain.school.SchoolRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.verification.dto.AdminVerificationResponse;
import com.grytonems.backend.domain.verification.dto.DocumentSubmitResponse;
import com.grytonems.backend.domain.verification.dto.MyVerificationResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.file.FileStorage;

import lombok.RequiredArgsConstructor;

/**
 * 서류 인증(AUTH-07)과 관리자 심사(ADMN-03).
 * 학생증·재학증명서 사진은 공개하지 않는 폴더(documents/)에 두고, 승인·반려하는 즉시 지웁니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentVerificationService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolVerificationRepository verificationRepository;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;

    // ===== 사용자 =====

    @Transactional
    public DocumentSubmitResponse submit(Long userId, Long schoolId, MultipartFile file) {
        LocalDate today = LocalDate.now();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SUSPENDED);
        }
        // 재인증 기간에는 인증 회원도 다시 신청할 수 있습니다 (AUTH-08)
        if (user.getStatus() == UserStatus.VERIFIED && ReverifyPolicy.dueDateOf(user, today) == null) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED);
        }
        if (verificationRepository.existsByUserIdAndMethodAndStatus(userId, VerificationMethod.DOCUMENT,
                VerificationStatus.PENDING)) {
            throw new BusinessException(ErrorCode.VERIFICATION_PENDING);
        }
        School school = schoolRepository.findById(schoolId)
                .filter(School::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_NOT_FOUND));

        String path = fileStorage.saveImage("documents", String.valueOf(userId), file);
        SchoolVerification saved = verificationRepository.save(
                SchoolVerification.documentPending(user, school, path, Semester.of(today)));
        return new DocumentSubmitResponse(saved.getId(), saved.getStatus());
    }

    /** 가장 최근 신청. 없으면 null */
    public MyVerificationResponse getMine(Long userId) {
        return verificationRepository.findFirstByUserIdOrderByIdDesc(userId)
                .map(MyVerificationResponse::from)
                .orElse(null);
    }

    // ===== 관리자 (ADMN-03) =====

    public CursorPage<AdminVerificationResponse> getList(VerificationStatus status, Long cursor, Integer size) {
        VerificationStatus target = status == null ? VerificationStatus.PENDING : status;
        int pageSize = CursorPage.normalizeSize(size);
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<SchoolVerification> fetched = target == VerificationStatus.PENDING
                ? verificationRepository.findOldestFirst(VerificationMethod.DOCUMENT, target, cursor, limit)
                : verificationRepository.findNewestFirst(VerificationMethod.DOCUMENT, target, cursor, limit);
        return CursorPage.of(fetched, pageSize, SchoolVerification::getId, AdminVerificationResponse::from);
    }

    /** 서류 사진 내려받기 (관리자). 처리된 신청은 사진이 지워져 404 */
    public DocumentFile getDocument(Long verificationId) {
        SchoolVerification v = getDocumentVerification(verificationId);
        if (v.getDocumentUrl() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "서류 사진이 없어요");
        }
        String contentType = v.getDocumentUrl().endsWith(".png") ? "image/png" : "image/jpeg";
        return new DocumentFile(fileStorage.read(v.getDocumentUrl()), contentType);
    }

    public record DocumentFile(byte[] bytes, String contentType) {
    }

    @Transactional
    public AdminVerificationResponse approve(Long adminId, Long verificationId) {
        LocalDateTime now = LocalDateTime.now();
        SchoolVerification v = getPending(verificationId);
        String path = v.getDocumentUrl();
        v.approve(userRepository.getReferenceById(adminId), now);
        v.getUser().verifyByDocument(v.getSchool(), now);
        fileStorage.delete(path);
        notificationService.notify(v.getUser().getId(), NotificationType.VERIFY_RESULT,
                NotificationTargetType.VERIFICATION, v.getId(), "학교 인증이 완료됐어요");
        return AdminVerificationResponse.from(v);
    }

    @Transactional
    public AdminVerificationResponse reject(Long adminId, Long verificationId, String reason) {
        LocalDateTime now = LocalDateTime.now();
        SchoolVerification v = getPending(verificationId);
        String path = v.getDocumentUrl();
        String trimmed = reason.trim();
        v.reject(userRepository.getReferenceById(adminId), trimmed, now);
        fileStorage.delete(path);
        notificationService.notify(v.getUser().getId(), NotificationType.VERIFY_RESULT,
                NotificationTargetType.VERIFICATION, v.getId(), truncate("학교 인증이 반려됐어요: " + trimmed, 200));
        return AdminVerificationResponse.from(v);
    }

    private SchoolVerification getPending(Long verificationId) {
        SchoolVerification v = getDocumentVerification(verificationId);
        if (v.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.VERIFICATION_ALREADY_REVIEWED);
        }
        return v;
    }

    private SchoolVerification getDocumentVerification(Long verificationId) {
        return verificationRepository.findById(verificationId)
                .filter(v -> v.getMethod() == VerificationMethod.DOCUMENT)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "인증 신청을 찾을 수 없어요"));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
