package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.post.dto.PostCardResponse;
import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.domain.post.dto.PostDetailResponse;
import com.grytonems.backend.domain.block.BlockPolicy;
import com.grytonems.backend.domain.post.dto.PostSearchCondition;
import com.grytonems.backend.domain.scrap.ScrapRepository;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 모집글 기능의 규칙과 처리 (기능 명세서 POST, LIST)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    /** 팀장 1인당 모집 중인 글 최대 개수 */
    private static final int MAX_OPEN_POSTS = 3;

    /** 검색어 최소 글자 수 (LIST-04) */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final ScrapRepository scrapRepository;
    private final BlockPolicy blockPolicy;

    /** POST-01: 모집글 작성 (글 + 역할 + 행사를 한 번에 저장) */
    @Transactional
    public PostCreateResponse createPost(Long leaderId, PostCreateRequest request) {
        // ① 여러 칸을 같이 보는 규칙 검사
        validate(request);

        // ② 모집 중인 글 3개 제한
        long openCount = postRepository.countByLeaderIdAndStatusAndHiddenFalseAndDeletedAtIsNull(leaderId, RecruitStatus.OPEN);
        if (openCount >= MAX_OPEN_POSTS) {
            throw new BusinessException(ErrorCode.POST_LIMIT_EXCEEDED);
        }

        // ③-1 글 저장
        Post post = postRepository.save(Post.builder()
                .leaderId(leaderId)
                .title(request.title())
                .category(request.category())
                .description(request.description())
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .deadline(request.deadline())
                .meetingType(request.meetingType())
                .locationCode(request.locationCode())
                .locationText(request.locationText())
                .meetingFreq(request.meetingFreq())
                .contactLink(request.contactLink())
                .build());

        // ③-2 역할 저장 (역할마다 한 줄씩)
        for (PostCreateRequest.RoleRequest role : request.roles()) {
            postRoleRepository.save(PostRole.builder()
                    .post(post)
                    .roleCode(role.roleCode())
                    .roleName(role.roleName())
                    .headcount(role.headcount())
                    .requirements(role.requirements())
                    .build());
        }

        // ③-3 행사 저장 (공모전·대회일 때만)
        if (needsEvent(request.category())) {
            PostCreateRequest.EventRequest event = request.event();
            eventRepository.save(Event.builder()
                    .post(post)
                    .name(event.name())
                    .host(event.host())
                    .officialUrl(event.officialUrl())
                    .applyDeadline(event.applyDeadline())
                    .build());
        }

        // ④ 만든 글 id 돌려주기
        return new PostCreateResponse(post.getId());
    }

    /** LIST-01~04: 모집글 목록 (커서 방식, 정렬·필터·검색) */
    public CursorPage<PostCardResponse> getPosts(PostSearchCondition condition, Long loginUserId) {
        PostSort postSort = PostSort.from(condition.sort());
        int pageSize = CursorPage.normalizeSize(condition.size());
        LocalDate today = LocalDate.now();

        // ① 조건 블록 조립 (기본 + 커서 + 고른 필터만)
        Specification<Post> spec = PostSpecs.visible().and(PostSpecs.leaderNotSuspended());
        spec = addCursor(spec, postSort, condition.cursor(), today);
        spec = addFilters(spec, condition);
        // LIST-06: 차단 관계인 사람이 팀장인 글은 빼기 (비회원은 해당 없음)
        Set<Long> blockedUserIds = blockPolicy.relatedUserIds(loginUserId);
        if (!blockedUserIds.isEmpty()) {
            spec = spec.and(PostSpecs.leaderNotIn(blockedUserIds));
        }

        // ② 정렬 정하고 21개 꺼내기
        Sort order = (postSort == PostSort.LATEST)
                ? Sort.by(Sort.Direction.DESC, "id")
                : Sort.by(Sort.Direction.ASC, "deadline").and(Sort.by(Sort.Direction.ASC, "id"));
        List<Post> posts = postRepository.findBy(spec, q -> q.sortBy(order).limit(pageSize + 1).all());

        if (posts.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // ③ 글들의 역할을 한 번에 꺼내서 글 id별로 묶기
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostRole>> rolesByPostId = postRoleRepository.findAllByPostIdInOrderByIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(role -> role.getPost().getId()));

        // ④ 팀장들을 한 번에 꺼내서 id로 찾기 쉽게 만들기
        List<Long> leaderIds = posts.stream().map(Post::getLeaderId).distinct().toList();
        Map<Long, User> leadersById = userRepository.findAllWithSchoolByIdIn(leaderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // ④-2 로그인했으면 이 글들 중 내가 스크랩한 글 (SCRP-01 북마크 표시, 비회원은 비어 있음)
        Set<Long> scrappedPostIds = loginUserId == null ? Set.of()
                : Set.copyOf(scrapRepository.findScrappedPostIds(loginUserId, postIds));

        // ⑤ 카드로 바꾸고 다음 커서 만들기 (CursorPage가 20개로 자르고 hasNext 판단)
        return CursorPage.of(posts, pageSize,
                post -> nextCursorOf(postSort, post),
                post -> PostCardResponse.of(post,
                        rolesByPostId.getOrDefault(post.getId(), List.of()),
                        leadersById.get(post.getLeaderId()),
                        today,
                        scrappedPostIds.contains(post.getId())));
    }

    /** 커서 블록: 최신순은 id, 마감 임박순은 "마감일_id" */
    private Specification<Post> addCursor(Specification<Post> spec, PostSort sort, String cursor, LocalDate today) {
        if (sort == PostSort.LATEST) {
            return isBlank(cursor) ? spec : spec.and(PostSpecs.idLessThan(parseId(cursor)));
        }

        // 마감 임박순은 마감일 지난 글을 빼고 시작
        spec = spec.and(PostSpecs.deadlineOnOrAfter(today));
        if (isBlank(cursor)) {
            return spec;
        }
        String[] parts = cursor.split("_");
        if (parts.length != 2) {
            throw invalid("잘못된 cursor 값이에요");
        }
        LocalDate cursorDeadline;
        try {
            cursorDeadline = LocalDate.parse(parts[0]);
        } catch (RuntimeException e) {
            throw invalid("잘못된 cursor 값이에요");
        }
        return spec.and(PostSpecs.afterDeadlineCursor(cursorDeadline, parseId(parts[1])));
    }

    /** 필터·검색 블록: 값이 있는 것만 붙이기 (LIST-03·04) */
    private Specification<Post> addFilters(Specification<Post> spec, PostSearchCondition c) {
        // 모집중만 보기: 기본 켜짐 (false를 보냈을 때만 끔)
        if (c.openOnly() == null || c.openOnly()) {
            spec = spec.and(PostSpecs.openOnly());
        }
        if (hasValues(c.categories())) {
            spec = spec.and(PostSpecs.categoryIn(c.categories()));
        }
        if (hasValues(c.roleCodes())) {
            spec = spec.and(PostSpecs.roleCodeIn(c.roleCodes()));
        }
        if (hasValues(c.schoolIds())) {
            spec = spec.and(PostSpecs.leaderSchoolIn(c.schoolIds()));
        }
        if (hasValues(c.locationCodes())) {
            spec = spec.and(PostSpecs.locationCodeIn(c.locationCodes()));
        }
        if (hasValues(c.meetingTypes())) {
            spec = spec.and(PostSpecs.meetingTypeIn(c.meetingTypes()));
        }
        if (c.period() != null) {
            spec = spec.and(PostSpecs.periodIn(c.period()));
        }
        if (!isBlank(c.keyword())) {
            String keyword = c.keyword().trim();
            if (keyword.length() < MIN_KEYWORD_LENGTH) {
                throw invalid("검색어는 2자 이상 입력해 주세요");
            }
            spec = spec.and(PostSpecs.keywordContains(keyword));
        }
        return spec;
    }

    /** LIST-05: 모집글 상세 (비회원도 가능. 로그인했으면 isMine·myApplication 채움) */
    public PostDetailResponse getPost(Long postId, Long loginUserId) {
        // ① 글 꺼내기 (없거나 숨김 글이면 NOT_FOUND, 삭제된 글이면 POST_DELETED)
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isHidden())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (post.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.POST_DELETED);
        }
        // 차단 관계면 못 봄 (내가 차단: 403 BLOCKED_USER, 나를 차단: 404)
        blockPolicy.checkVisible(loginUserId, post.getLeaderId(), "모집글을 찾을 수 없어요");

        // ② 역할·행사·팀장 꺼내기
        List<PostRole> roles = postRoleRepository.findAllByPostIdOrderByIdAsc(postId);
        Event event = eventRepository.findByPostId(postId).orElse(null);
        User leader = userRepository.findById(post.getLeaderId()).orElse(null);
        // ADMN-04: 팀장이 정지 중이면 숨김 글처럼 404
        if (leader != null && leader.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // ③ 로그인한 사람 기준 정보
        boolean isMine = loginUserId != null && loginUserId.equals(post.getLeaderId());
        Application myApplication = (loginUserId == null || isMine)
                ? null
                : applicationRepository.findByPostIdAndApplicantId(postId, loginUserId).orElse(null);

        // ④ 한 상자에 담기
        // ④ 내가 스크랩한 글인지 (SCRP-01 북마크 표시)
        boolean isScrapped = loginUserId != null && scrapRepository.existsByUserIdAndPostId(loginUserId, postId);

        return PostDetailResponse.of(post, roles, event, leader, isMine, myApplication, isScrapped, LocalDate.now());
    }

    /** POST-04: 수동 마감 (팀장만). 글·역할 마감 + 대기 지원 마감 + 대기자에게 알림 */
    @Transactional
    public void closePost(Long loginUserId, Long postId) {
        // ① 글 꺼내기 + 팀장인지, 이미 마감됐는지 확인
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isHidden() && p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!post.getLeaderId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (post.getStatus() == RecruitStatus.CLOSED) {
            throw new BusinessException(ErrorCode.POST_ALREADY_CLOSED);
        }

        // ② 글과 역할 마감 (아래 ③보다 먼저 해야 저장돼요)
        List<PostRole> roles = postRoleRepository.findAllByPostIdOrderByIdAsc(postId);
        post.close();
        roles.forEach(PostRole::close);

        // ③ 역할마다 대기 중인 지원자를 알림 대상으로 기억하고, 그 지원들을 마감
        List<Long> waitingApplicantIds = new ArrayList<>();
        for (PostRole role : roles) {
            waitingApplicantIds.addAll(applicationRepository
                    .findApplicantIdsByPostRoleIdAndStatus(role.getId(), ApplicationStatus.PENDING));
            applicationRepository.closePendingByPostRoleId(role.getId(),
                    ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
        }

        // ④ 대기자들에게 알림 (현찬님 수락 자동 마감과 같은 문구)
        notificationService.notifyAll(waitingApplicantIds, NotificationType.POST_CLOSED,
                NotificationTargetType.POST, postId, "'" + post.getTitle() + "' 모집이 마감되었어요");
    }

    /** POST-03: 삭제 (팀장만, 지원자가 없을 때만). DB에는 삭제 표시로 남깁니다 */
    @Transactional
    public void deletePost(Long loginUserId, Long postId) {
        // ① 글 꺼내기 + 팀장인지 확인 (이미 삭제됐거나 숨김 글이면 404)
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isHidden() && p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!post.getLeaderId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // ② 지원자(취소한 사람 제외)가 한 명이라도 있으면 삭제 불가 → 마감 안내
        boolean hasApplicants = applicationRepository.countByRoleAndStatus(List.of(postId)).stream()
                .anyMatch(row -> row.status() != ApplicationStatus.CANCELED);
        if (hasApplicants) {
            throw new BusinessException(ErrorCode.POST_HAS_APPLICANTS);
        }

        // ③ 삭제 표시
        post.delete();
    }

    private String nextCursorOf(PostSort sort, Post post) {
        return (sort == PostSort.LATEST)
                ? String.valueOf(post.getId())
                : post.getDeadline() + "_" + post.getId();
    }

    private long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid("잘못된 cursor 값이에요");
        }
    }

    /** 명세서 POST-01 규칙 중 여러 칸을 함께 봐야 하는 것들 */
    private void validate(PostCreateRequest request) {
        LocalDate today = LocalDate.now();

        if (request.periodStart().isAfter(request.periodEnd())) {
            throw invalid("활동 종료일은 시작일과 같거나 이후로 입력해 주세요");
        }
        if (request.deadline().isBefore(today)) {
            throw invalid("모집 마감일은 오늘 이후로 입력해 주세요");
        }
        if (request.deadline().isAfter(request.periodStart())) {
            throw invalid("모집 마감일은 활동 시작일과 같거나 이전이어야 해요");
        }

        // 대면·혼합이면 장소와 모임 빈도 필수
        if (request.meetingType() != MeetingType.ONLINE) {
            if (request.locationCode() == null) {
                throw invalid("모임 장소를 선택해 주세요");
            }
            if (isBlank(request.meetingFreq())) {
                throw invalid("모임 빈도를 입력해 주세요");
            }
        }

        // 캠퍼스·직접 입력이면 장소 글자 필수
        if ((request.locationCode() == LocationCode.CAMPUS || request.locationCode() == LocationCode.CUSTOM)
                && isBlank(request.locationText())) {
            throw invalid("장소를 입력해 주세요");
        }

        // 공모전·대회면 행사 정보 필수
        if (needsEvent(request.category()) && request.event() == null) {
            throw invalid("공모전·대회는 행사 정보를 입력해 주세요");
        }
    }

    private boolean needsEvent(PostCategory category) {
        return category == PostCategory.CONTEST || category == PostCategory.COMPETITION;
    }

    private boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}