package com.grytonems.backend.domain.user;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.user.dto.LoginRequest;
import com.grytonems.backend.domain.user.dto.LoginResponse;
import com.grytonems.backend.domain.user.dto.SignupRequest;
import com.grytonems.backend.domain.user.dto.UserResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.security.JwtProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final DateTimeFormatter LOCK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /** AUTH-01: 이메일 회원가입. 가입하면 바로 로그인 상태로 토큰을 돌려줍니다. */
    @Transactional
    public LoginResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = request.nickname().trim();

        if (nickname.length() < 2) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 2~12자로 입력해 주세요");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(nickname)
                .build());

        return toLoginResponse(user);
    }

    /**
     * AUTH-04: 로그인.
     * - 이메일이 없거나 비밀번호가 틀리면 어느 쪽인지 알려주지 않고 LOGIN_FAILED
     * - 5회 연속 실패하면 10분 잠금 (LOGIN_LOCKED)
     *
     * 실패 횟수는 예외를 던져도 저장돼야 하므로 BusinessException 에서는 롤백하지 않습니다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(u -> u.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        if (user.isLocked(now)) {
            throw lockedException(user);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            boolean locked = user.recordLoginFailure(now);
            throw locked ? lockedException(user) : new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        user.recordLoginSuccess();
        return toLoginResponse(user);
    }

    private LoginResponse toLoginResponse(User user) {
        return new LoginResponse(jwtProvider.createAccessToken(user.getId()), UserResponse.from(user));
    }

    private BusinessException lockedException(User user) {
        String until = user.getLockedUntil().format(LOCK_TIME_FORMAT);
        return new BusinessException(ErrorCode.LOGIN_LOCKED,
                "로그인에 5회 실패해 " + until + "까지 로그인할 수 없어요");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
