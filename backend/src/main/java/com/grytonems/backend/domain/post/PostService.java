package com.grytonems.backend.domain.post;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.post.dto.PostCreateRequest;
import com.grytonems.backend.domain.post.dto.PostCreateResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 모집글 기능의 규칙과 처리 (기능 명세서 POST)
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