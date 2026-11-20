package com.grytonems.backend.domain.profile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.block.BlockPolicy;
import com.grytonems.backend.domain.post.RoleCode;
import com.grytonems.backend.domain.profile.dto.ActivityResponse;
import com.grytonems.backend.domain.profile.dto.ProfileImageResponse;
import com.grytonems.backend.domain.profile.dto.ProfileItemRequest;
import com.grytonems.backend.domain.profile.dto.ProfileItemResponse;
import com.grytonems.backend.domain.profile.dto.ProfileResponse;
import com.grytonems.backend.domain.profile.dto.ProfileUpdateRequest;
import com.grytonems.backend.domain.profile.dto.RoleTagsUpdateRequest;
import com.grytonems.backend.domain.profile.dto.SkillsUpdateRequest;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.global.file.FileStorage;

import lombok.RequiredArgsConstructor;

/**
 * 프로필 1단계: 기본 정보(PROF-01), 희망 역할 태그(PROF-02), 보유 기술(PROF-03), 타인 프로필(PROF-12)
 * 프로필 2단계: 이미지(PROF-01), 이력 항목(PROF-04~07, 09), 활동 이력(PROF-08), 완성도(PROF-10), 지원자 프로필(APPL-02)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    public static final int MAX_ROLE_TAGS = 3;
    public static final int MAX_SKILLS = 10;
    public static final int MAX_PORTFOLIOS = 5;

    /** 프로필을 보는 사람: 본인 / 다른 사람 / 내 글에 지원한 사람을 보는 팀장 */
    enum Viewer { SELF, OTHER, LEADER }

    private final UserRepository userRepository;
    private final UserRoleTagRepository roleTagRepository;
    private final UserSkillRepository skillRepository;
    private final BlockPolicy blockPolicy;
    private final ProfileItemRepository itemRepository;
    private final ApplicationRepository applicationRepository;
    private final FileStorage fileStorage;

    /** 내 프로필 조회. 탈퇴한 사용자는 없는 사용자로 취급 */
    public ProfileResponse getProfile(Long userId) {
        return toResponse(getUser(userId), Viewer.SELF);
    }

    /** PROF-12 타인 프로필. 차단 관계면 못 봄 (내가 차단: 403 BLOCKED_USER, 나를 차단: 404) */
    public ProfileResponse getOtherProfile(Long viewerId, Long userId) {
        User user = getUser(userId);
        blockPolicy.checkVisible(viewerId, userId, "사용자를 찾을 수 없어요");
        return toResponse(user, viewerId.equals(userId) ? Viewer.SELF : Viewer.OTHER);
    }

    /** PROF-01 기본 정보 수정: 학과, 학년, 한 줄 소개 */
    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = getUser(userId);
        user.updateProfile(blankToNull(request.major()), request.grade(), blankToNull(request.bio()));
        return toResponse(user, Viewer.SELF);
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
        return toResponse(user, Viewer.SELF);
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
        return toResponse(user, Viewer.SELF);
    }

    // ===== APPL-02 지원자 프로필 =====

    /** 그 글의 팀장만, "지원한 팀장에게만" 항목까지 봅니다 */
    public ProfileResponse getApplicantProfile(Long leaderId, Long applicationId) {
        Application application = applicationRepository.findWithPostAndRoleById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지원을 찾을 수 없어요"));
        if (!application.getPost().getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "내 글에 온 지원이 아니에요");
        }
        return toResponse(getUser(application.getApplicant().getId()), Viewer.LEADER);
    }

    // ===== PROF-01 프로필 이미지 =====

    @Transactional
    public ProfileImageResponse changeImage(Long userId, MultipartFile file) {
        User user = getUser(userId);
        String relative = fileStorage.saveImage("profiles", String.valueOf(userId), file);
        String old = fileStorage.relativeOfPublicUrl(user.getProfileImageUrl());
        user.changeProfileImage(fileStorage.publicUrlOf(relative));
        fileStorage.delete(old);
        return new ProfileImageResponse(user.getProfileImageUrl());
    }

    @Transactional
    public ProfileImageResponse deleteImage(Long userId) {
        User user = getUser(userId);
        fileStorage.delete(fileStorage.relativeOfPublicUrl(user.getProfileImageUrl()));
        user.changeProfileImage(null);
        return new ProfileImageResponse(null);
    }

    // ===== PROF-04~07, 09 이력 항목 =====

    @Transactional
    public ProfileItemResponse addItem(Long userId, ProfileItemRequest request) {
        User user = getUser(userId);
        if (request.itemType() == ProfileItemType.PORTFOLIO
                && itemRepository.countByUserIdAndItemTypeAndDeletedAtIsNull(userId, ProfileItemType.PORTFOLIO)
                        >= MAX_PORTFOLIOS) {
            throw new BusinessException(ErrorCode.PORTFOLIO_LIMIT_EXCEEDED);
        }
        ProfileItem item = ProfileItem.builder().user(user).itemType(request.itemType()).build();
        apply(item, request);
        return ProfileItemResponse.from(itemRepository.save(item));
    }

    @Transactional
    public ProfileItemResponse updateItem(Long userId, Long itemId, ProfileItemRequest request) {
        ProfileItem item = getItem(userId, itemId);
        if (request.itemType() != item.getItemType()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "항목 종류는 바꿀 수 없어요");
        }
        apply(item, request);
        return ProfileItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        getItem(userId, itemId).delete(LocalDateTime.now());
    }

    /** 종류별 필수 칸을 확인하고, 그 종류에 쓰지 않는 칸은 비워서 저장합니다 */
    private void apply(ProfileItem item, ProfileItemRequest r) {
        String title = r.title().trim();
        switch (item.getItemType()) {
            case EXPERIENCE -> {
                require(r.subType() != null, "활동 유형을 골라 주세요");
                require(r.startDate() != null, "시작일을 입력해 주세요");
                require(r.endDate() == null || !r.endDate().isBefore(r.startDate()), "종료일은 시작일 이후여야 해요");
                item.update(r.subType(), title, blankToNull(r.role()), null, r.startDate(), r.endDate(),
                        blankToNull(r.description()), null, r.visibility());
            }
            case AWARD, CERTIFICATE -> {
                require(StringUtils.hasText(r.issuer()),
                        item.getItemType() == ProfileItemType.AWARD ? "수상 내역을 입력해 주세요" : "발급 기관을 입력해 주세요");
                require(r.startDate() != null,
                        item.getItemType() == ProfileItemType.AWARD ? "수상일을 입력해 주세요" : "취득일을 입력해 주세요");
                item.update(null, title, null, r.issuer().trim(), r.startDate(), null, null, null, r.visibility());
            }
            case PORTFOLIO -> {
                String url = r.url() == null ? "" : r.url().trim();
                require(url.matches("(?i)^https?://\\S+$"), "http:// 또는 https:// 로 시작하는 링크를 입력해 주세요");
                item.update(null, title, null, null, null, null, null, url, r.visibility());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
    }

    private ProfileItem getItem(Long userId, Long itemId) {
        return itemRepository.findByIdAndUserIdAndDeletedAtIsNull(itemId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
    }

    // ===== PROF-08 활동 이력 노출 =====

    @Transactional
    public ProfileResponse changeActivityVisibility(Long userId, boolean showActivity) {
        User user = getUser(userId);
        user.changeShowActivity(showActivity);
        return toResponse(user, Viewer.SELF);
    }

    // ===== 응답 조립 =====

    private ProfileResponse toResponse(User user, Viewer viewer) {
        List<RoleCode> roleTags = roleTagRepository.findAllByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserRoleTag::getRoleCode)
                .toList();
        List<String> skills = skillRepository.findAllByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserSkill::getName)
                .toList();
        List<ProfileItem> allItems = itemRepository.findAllByUserIdAndDeletedAtIsNullOrderByIdAsc(user.getId());
        List<ProfileItemResponse> items = sortItems(allItems).stream()
                .filter(item -> viewer != Viewer.OTHER || item.getVisibility() == ItemVisibility.PUBLIC)
                .map(ProfileItemResponse::from)
                .toList();
        List<ActivityResponse> activities = (viewer == Viewer.SELF || user.isShowActivity())
                ? activities(user.getId())
                : List.of();
        boolean self = viewer == Viewer.SELF;
        return ProfileResponse.of(user, roleTags, skills, items, activities,
                self ? user.isShowActivity() : null,
                self ? completeness(user, roleTags, skills, allItems) : null);
    }

    private List<ActivityResponse> activities(Long userId) {
        return applicationRepository.findActivities(userId, List.of(ApplicationStatus.ACCEPTED, ApplicationStatus.LEFT))
                .stream()
                .map(a -> new ActivityResponse(a.getPost().getId(), a.getPost().getTitle(), a.getPostRole().getRoleName(),
                        a.getPost().getPeriodStart(), a.getPost().getPeriodEnd(),
                        a.getStatus() == ApplicationStatus.LEFT))
                .toList();
    }

    /** 종류 순서(경력·수상·자격증·포트폴리오)대로, 경력·수상·자격증은 날짜 최신순, 포트폴리오는 등록순 */
    private static List<ProfileItem> sortItems(List<ProfileItem> items) {
        Comparator<ProfileItem> byType = Comparator.comparing(ProfileItem::getItemType);
        Comparator<ProfileItem> byDate = Comparator.comparing(
                (ProfileItem item) -> item.getItemType() == ProfileItemType.PORTFOLIO ? null : item.getStartDate(),
                Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()));
        return items.stream()
                .sorted(byType.thenComparing(byDate).thenComparing(ProfileItem::getId))
                .toList();
    }

    /**
     * PROF-10 프로필 완성도 (0~100): 학과·학년·한 줄 소개·이미지 각 10, 희망 역할 20, 보유 기술 10,
     * 경력 10, 포트폴리오 10, 수상·자격증 중 하나 10
     */
    static int completeness(User user, List<RoleCode> roleTags, List<String> skills, List<ProfileItem> items) {
        int score = 0;
        score += StringUtils.hasText(user.getMajor()) ? 10 : 0;
        score += user.getGrade() != null ? 10 : 0;
        score += StringUtils.hasText(user.getBio()) ? 10 : 0;
        score += StringUtils.hasText(user.getProfileImageUrl()) ? 10 : 0;
        score += roleTags.isEmpty() ? 0 : 20;
        score += skills.isEmpty() ? 0 : 10;
        score += items.stream().anyMatch(i -> i.getItemType() == ProfileItemType.EXPERIENCE) ? 10 : 0;
        score += items.stream().anyMatch(i -> i.getItemType() == ProfileItemType.PORTFOLIO) ? 10 : 0;
        score += items.stream().anyMatch(i -> i.getItemType() == ProfileItemType.AWARD
                || i.getItemType() == ProfileItemType.CERTIFICATE) ? 10 : 0;
        return score;
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
