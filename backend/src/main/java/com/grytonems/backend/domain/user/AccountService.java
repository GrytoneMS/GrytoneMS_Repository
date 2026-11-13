package com.grytonems.backend.domain.user;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostDeadlineService;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.profile.ProfileItemRepository;
import com.grytonems.backend.domain.profile.UserRoleTagRepository;
import com.grytonems.backend.domain.profile.UserSkillRepository;
import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.domain.user.dto.WithdrawalCheckResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.file.FileStorage;
import com.grytonems.backend.global.mail.EmailSender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 계정 관리: 닉네임 변경(AUTH-03), 비밀번호 재설정(AUTH-05), 회원 탈퇴(AUTH-09) */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenStore resetTokenStore;
    private final EmailSender emailSender;
    private final UserService userService;
    private final PostRepository postRepository;
    private final PostDeadlineService postDeadlineService;
    private final ApplicationRepository applicationRepository;
    private final UserRoleTagRepository roleTagRepository;
    private final UserSkillRepository skillRepository;
    private final ProfileItemRepository profileItemRepository;
    private final FileStorage fileStorage;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ===== AUTH-03 닉네임 변경 =====

    @Transactional
    public UserResponse changeNickname(Long userId, String requested) {
        LocalDateTime now = LocalDateTime.now();
        User user = getActiveUser(userId);
        String nickname = requested.trim();
        if (nickname.equals(user.getNickname())) {
            return userService.getMe(userId);
        }
        LocalDate changeableAt = user.nicknameChangeableAt(now);
        if (changeableAt != null) {
            long days = ChronoUnit.DAYS.between(now.toLocalDate(), changeableAt);
            throw new BusinessException(ErrorCode.NICKNAME_CHANGE_TOO_SOON, days + "일 후에 바꿀 수 있어요");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }
        user.changeNickname(nickname, now);
        userRepository.flush(); // 같은 순간 같은 닉네임 저장은 UNIQUE 제약으로 막힙니다
        return userService.getMe(userId);
    }

    // ===== AUTH-05 비밀번호 재설정 =====

    /** 가입 여부를 드러내지 않도록, 없는 이메일이어도 조용히 끝냅니다 */
    public void requestPasswordReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email)
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElse(null);
        if (user == null) {
            return;
        }
        String token = newToken();
        resetTokenStore.save(token, user.getId(), RESET_TOKEN_TTL);
        String link = frontendUrl.replaceAll("/+$", "") + "/reset-password?token=" + token;
        emailSender.send(email, "[팀업] 비밀번호 재설정 안내",
                "아래 링크에서 새 비밀번호를 설정해 주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.\n\n" + link
                        + "\n\n요청하지 않았다면 이 메일을 무시해 주세요.");
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        Long userId = resetTokenStore.consume(token.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        User user = userRepository.findById(userId)
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    // ===== AUTH-09 회원 탈퇴 =====

    public WithdrawalCheckResponse withdrawalCheck(Long userId) {
        long openPosts = postRepository.countByLeaderIdAndStatusAndHiddenFalseAndDeletedAtIsNull(userId,
                RecruitStatus.OPEN);
        long acceptedTeams = applicationRepository.countByApplicantIdAndStatus(userId, ApplicationStatus.ACCEPTED);
        return new WithdrawalCheckResponse(openPosts, acceptedTeams);
    }

    /**
     * 모집 중인 내 글은 마감(대기 지원자에게 POST_CLOSED), 내 대기 지원은 취소하고,
     * 개인정보를 비운 뒤 탈퇴 상태로 둡니다. 이후 토큰은 401입니다.
     */
    @Transactional
    public void withdraw(Long userId, String password) {
        LocalDateTime now = LocalDateTime.now();
        User user = getActiveUser(userId);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 아래 일괄 변경이 영속성 컨텍스트를 비우므로 글은 하나씩 다시 읽어 마감합니다
        List<Long> openPostIds = postRepository.findMine(userId, RecruitStatus.OPEN, null, PageRequest.of(0, 100))
                .stream().map(Post::getId).toList();
        for (Long postId : openPostIds) {
            postRepository.findById(postId).ifPresent(postDeadlineService::closeWithApplicants);
        }
        applicationRepository.cancelAllPending(userId, now, ApplicationStatus.PENDING, ApplicationStatus.CANCELED);
        roleTagRepository.deleteAllByUserId(userId);
        skillRepository.deleteAllByUserId(userId);
        profileItemRepository.deleteAllByUserId(userId);

        User withdrawing = getActiveUser(userId);
        fileStorage.delete(fileStorage.relativeOfPublicUrl(withdrawing.getProfileImageUrl()));
        withdrawing.withdraw(now);
        log.info("회원 탈퇴 userId={}", userId);
    }

    private User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
