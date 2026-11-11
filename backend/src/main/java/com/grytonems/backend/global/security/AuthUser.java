package com.grytonems.backend.global.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRole;
import com.grytonems.backend.domain.user.UserStatus;

/**
 * 로그인한 사용자. 컨트롤러에서 이렇게 꺼내 씁니다.
 *
 * <pre>
 * public ApiResponse&lt;...&gt; create(@AuthenticationPrincipal AuthUser loginUser, ...) {
 *     Long userId = loginUser.id();
 * }
 * </pre>
 */
public record AuthUser(Long id, UserRole role, UserStatus status) {

    /** 학교 인증 회원만 가진 권한 (글쓰기·지원) */
    public static final String VERIFIED_AUTHORITY = "VERIFIED";

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getRole(), user.getStatus());
    }

    public List<GrantedAuthority> authorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (status == UserStatus.VERIFIED) {
            authorities.add(new SimpleGrantedAuthority(VERIFIED_AUTHORITY));
        }
        return authorities;
    }
}
