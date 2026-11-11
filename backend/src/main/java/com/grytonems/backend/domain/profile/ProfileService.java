package com.grytonems.backend.domain.profile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.profile.dto.ProfileResponse;
import com.grytonems.backend.domain.profile.dto.ProfileUpdateRequest;
import com.grytonems.backend.domain.profile.dto.RoleTagsUpdateRequest;
import com.grytonems.backend.domain.profile.dto.SkillsUpdateRequest;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 프로필 1단계: 기본 정보(PROF-01), 희망 역할 태그(PROF-02), 보유 기술(PROF-03), 타인 프로필(PROF-12) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    public static final int MAX_ROLE_TAGS = 3;
    public static final int MAX_SKILLS = 10;

    private final UserRepository userRepository;
    private final UserRoleTagRepository roleTagRepository;
    private final UserSkillRepository skillRepository;

    /** 내 프로필·타인 프로필 조회. 탈퇴한 사용자는 없는 사용자로 취급 */
    public ProfileResponse getProfile(Long userId) {
        return toResponse(getUser(userId));
    }

    /** PROF-01 기본 정보 수정: 학과, 학년, 한 줄 소개 */
    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = getUser(userId);
        user.updateProfile(blankToNull(request.major()), request.grade(), blankToNull(request.bio()));
        return toResponse(user);
    }

    /** PROF-02 희망 역할 태그: 보낸 목록으로 통째로 교체, 최대 3개 */
    @Transactional
    public ProfileResponse updateRoleTags(Long userId, RoleTagsUpdateRequest request) {
        Set<RoleCode> codes = new LinkedHashSet<>(request.roleCodes()); // 중복 제거, 순서 유지
        if (codes.size() > MAX_ROLE_TAGS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "희망 역할은 최대 3개까지 고를 수 있어요");
        }
        roleTagRepository.deleteAllByUserId(userId);
        User user = getUser(userId);
        roleTagRepository.saveAll(codes.stream().map(code -> new UserRoleTag(user, code)).toList());
        return toResponse(user);
    }

    /** PROF-03 보유 기술·툴: 보낸 목록으로 통째로 교체, 중복 자동 제거, 최대 10개 */
    @Transactional
    public ProfileResponse updateSkills(Long userId, SkillsUpdateRequest request) {
        List<String> names = dedupeIgnoreCase(request.skills());
        if (names.size() > MAX_SKILLS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기술·툴은 최대 10개까지 등록할 수 있어요");
        }
        skillRepository.deleteAllByUserId(userId);
        User user = getUser(userId);
        skillRepository.saveAll(names.stream().map(name -> new UserSkill(user, name)).toList());
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        List<RoleCode> roleTags = roleTagRepository.findAllByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserRoleTag::getRoleCode)
                .toList();
        List<String> skills = skillRepository.findAllByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserSkill::getName)
                .toList();
        return ProfileResponse.of(user, roleTags, skills);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
    }

    /** 앞뒤 공백을 떼고, 대소문자만 다른 이름은 처음 것 하나만 남깁니다 (Figma, figma → Figma) */
    private static List<String> dedupeIgnoreCase(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String name = value.trim();
            if (seen.add(name.toLowerCase(Locale.ROOT))) {
                result.add(name);
            }
        }
        return result;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
