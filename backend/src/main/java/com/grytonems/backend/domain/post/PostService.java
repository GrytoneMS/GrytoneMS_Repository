package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.post.dto.PostCardResponse;
import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;
import com.grytonems.backend.domain.application.Application;
import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.post.dto.PostDetailResponse;

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

    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    /** POST-01: 모집글 작성 (글 + 역할 + 행사를 한 번에 저장) */
    @Transactional
    public PostCreateResponse createPost(Long leaderId, PostCreateRequest request) {
        // ① 여러 칸을 같이 보는 규칙 검사
        validate(request);

        // ② 모집 중인 글 3개 제한
        long openCount = postRepository.countByLeaderIdAndStatusAndDeletedAtIsNull(leaderId, RecruitStatus.OPEN);
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

    /** LIST-01·02: 모집글 목록 (커서 방식, 최신순 / 마감 임박순) */
    public CursorPage<PostCardResponse> getPosts(String sort, String cursor, Integer size) {
        PostSort postSort = PostSort.from(sort);
        int pageSize = CursorPage.normalizeSize(size);
        LocalDate today = LocalDate.now();
        Pageable limit = PageRequest.of(0, pageSize + 1); // 다음 페이지가 있는지 보려고 1개 더

        // ① 글 꺼내기 (정렬에 따라 다른 조회)
        List<Post> posts = (postSort == PostSort.LATEST)
                ? findLatest(cursor, limit)
                : findByDeadline(today, cursor, limit);

        if (posts.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // ② 글들의 역할을 한 번에 꺼내서 글 id별로 묶기
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, List<PostRole>> rolesByPostId = postRoleRepository.findAllByPostIdInOrderByIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(role -> role.getPost().getId()));

        // ③ 팀장들을 한 번에 꺼내서 id로 찾기 쉽게 만들기
        List<Long> leaderIds = posts.stream().map(Post::getLeaderId).distinct().toList();
        Map<Long, User> leadersById = userRepository.findAllById(leaderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        // ④ 카드로 바꾸고 다음 커서 만들기 (CursorPage가 20개로 자르고 hasNext 판단)
        return CursorPage.of(posts, pageSize,
                post -> nextCursorOf(postSort, post),
                post -> PostCardResponse.of(post,
                        rolesByPostId.getOrDefault(post.getId(), List.of()),
                        leadersById.get(post.getLeaderId()),
                        today));
    }

    /** LIST-05: 모집글 상세 (비회원도 가능. 로그인했으면 isMine·myApplication 채움) */
    public PostDetailResponse getPost(Long postId, Long loginUserId) {
        // ① 글 꺼내기 (없거나 숨김·삭제된 글이면 404)
        Post post = postRepository.findById(postId)
                .filter(p -> !p.isHidden() && p.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // ② 역할·행사·팀장 꺼내기
        List<PostRole> roles = postRoleRepository.findAllByPostIdOrderByIdAsc(postId);
        Event event = eventRepository.findByPostId(postId).orElse(null);
        User leader = userRepository.findById(post.getLeaderId()).orElse(null);

        // ③ 로그인한 사람 기준 정보
        boolean isMine = loginUserId != null && loginUserId.equals(post.getLeaderId());
        Application myApplication = (loginUserId == null || isMine)
                ? null
                : applicationRepository.findByPostIdAndApplicantId(postId, loginUserId).orElse(null);

        // ④ 한 상자에 담기
        return PostDetailResponse.of(post, roles, event, leader, isMine, myApplication, LocalDate.now());
    }

    /** 최신순: 커서 = 마지막 글 id. 첫 요청이면 가장 큰 숫자부터 */
    private List<Post> findLatest(String cursor, Pageable limit) {
        long cursorId = isBlank(cursor) ? Long.MAX_VALUE : parseId(cursor);
        return postRepository.findLatest(cursorId, limit);
    }

    /** 마감 임박순: 커서 = "마감일_id" (예: 2026-11-30_12). 첫 요청이면 어제 날짜부터 */
    private List<Post> findByDeadline(LocalDate today, String cursor, Pageable limit) {
        if (isBlank(cursor)) {
            return postRepository.findByDeadline(today, today.minusDays(1), 0L, limit);
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
        return postRepository.findByDeadline(today, cursorDeadline, parseId(parts[1]), limit);
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}