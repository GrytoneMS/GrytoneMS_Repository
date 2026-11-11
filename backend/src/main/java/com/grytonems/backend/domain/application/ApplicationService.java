package com.grytonems.backend.domain.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.dto.AcceptResponse;
import com.grytonems.backend.domain.application.dto.ApplyRequest;
import com.grytonems.backend.domain.application.dto.ApplyResponse;
import com.grytonems.backend.domain.application.dto.ContactResponse;
import com.grytonems.backend.domain.application.dto.MyApplicationResponse;
import com.grytonems.backend.domain.application.dto.RejectRequest;
import com.grytonems.backend.domain.application.dto.RejectResponse;
import com.grytonems.backend.domain.application.dto.RoleApplicantsResponse;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.RecruitStatus;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 지원 (APPL): 지원하기, 지원자 목록, 수락·거절, 연락 수단, 내 지원 내역 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final RoleSeatRepository roleSeatRepository;
    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * APPL-01 지원하기.
     * 본인 글·마감된 글이나 역할·이미 지원한 글에는 지원할 수 없습니다.
     * (학교 인증 회원만 가능하다는 조건은 SecurityConfig에서 먼저 걸러집니다.)
     */
    @Transactional
    public ApplyResponse apply(Long userId, Long postId, ApplyRequest request) {
        Post post = getVisiblePost(postId);
        if (post.getLeaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_OWN_POST);
        }
        PostRole role = postRoleRepository.findById(request.postRoleId())
                .filter(r -> r.getPost().getId().equals(postId))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "이 모집글의 역할이 아니에요"));

        if (applicationRepository.existsByPostIdAndApplicantId(postId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
        boolean deadlinePassed = LocalDate.now().isAfter(post.getDeadline());
        if (post.getStatus() != RecruitStatus.OPEN || role.getStatus() != RecruitStatus.OPEN || deadlinePassed) {
            throw new BusinessException(ErrorCode.ROLE_CLOSED);
        }

        User applicant = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        try {
            Application saved = applicationRepository.saveAndFlush(Application.builder()
                    .post(post)
                    .postRole(role)
                    .applicant(applicant)
                    .message(request.message().trim())
                    .availableTime(request.availableTime().trim())
                    .build());
            // 팀장에게 "새 지원 도착" 알림
            notificationService.notify(post.getLeaderId(), NotificationType.NEW_APPLICATION,
                    NotificationTargetType.POST, postId,
                    applicant.getNickname() + "님이 '" + role.getRoleName() + "'에 지원했어요");
            return new ApplyResponse(saved.getId(), saved.getStatus());
        } catch (DataIntegrityViolationException e) {
            // 지원 버튼을 동시에 두 번 누른 경우: DB의 UQ(post_id, applicant_id)가 막아 줍니다
            throw new BusinessException(ErrorCode.ALREADY_APPLIED);
        }
    }

    /** APPL-04 지원자 목록: 팀장만, 역할별로 묶어 지원 시간순 (취소한 지원 제외) */
    public List<RoleApplicantsResponse> getApplicants(Long userId, Long postId) {
        Post post = getExistingPost(postId);
        requireLeader(post, userId);

        Map<Long, List<Application>> byRole = applicationRepository
                .findApplicantsByPostId(postId, ApplicationStatus.CANCELED).stream()
                .collect(Collectors.groupingBy(a -> a.getPostRole().getId()));

        return postRoleRepository.findAllByPostIdOrderByIdAsc(postId).stream()
                .map(role -> RoleApplicantsResponse.of(role, byRole.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    /**
     * APPL-05 수락.
     * 1) 지원을 대기 → 수락으로 (대기일 때만)
     * 2) 역할 자리를 하나 채움 (자리가 남았을 때만, 아니면 전체 취소)
     * 3) 인원이 다 차면 역할 마감 + 그 역할의 남은 대기 지원 마감 (APPL-08)
     * 4) 모든 역할이 마감되면 글도 마감 (POST-06)
     */
    @Transactional
    public AcceptResponse accept(Long userId, Long applicationId) {
        Application application = getApplication(applicationId);
        requireLeader(application.getPost(), userId);
        Long postId = application.getPost().getId();
        Long roleId = application.getPostRole().getId();
        Long applicantId = application.getApplicant().getId();
        String postTitle = application.getPost().getTitle();

        int accepted = applicationRepository.markAccepted(applicationId, LocalDateTime.now(),
                ApplicationStatus.PENDING, ApplicationStatus.ACCEPTED);
        if (accepted == 0) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        if (roleSeatRepository.fillOneSeat(roleId, RecruitStatus.OPEN) == 0) {
            // 예외를 던지면 1)의 수락도 함께 취소(롤백)됩니다
            throw new BusinessException(ErrorCode.ROLE_FULL);
        }

        boolean roleClosed = roleSeatRepository.closeIfFull(roleId, RecruitStatus.OPEN, RecruitStatus.CLOSED) == 1;
        boolean postClosed = false;
        if (roleClosed) {
            // 마감 처리 전에, 이 역할에 아직 대기 중이던 지원자들을 알림 대상으로 기억해 둡니다
            List<Long> waitingApplicantIds = applicationRepository
                    .findApplicantIdsByPostRoleIdAndStatus(roleId, ApplicationStatus.PENDING);
            applicationRepository.closePendingByPostRoleId(roleId, ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
            notificationService.notifyAll(waitingApplicantIds, NotificationType.POST_CLOSED,
                    NotificationTargetType.POST, postId, "'" + postTitle + "' 모집이 마감되었어요");
            if (roleSeatRepository.countOpenRoles(postId, RecruitStatus.OPEN) == 0) {
                postRepository.findById(postId).ifPresent(Post::close);
                postClosed = true;
            }
        }
        notificationService.notify(applicantId, NotificationType.ACCEPTED, NotificationTargetType.POST, postId,
                "축하해요! '" + postTitle + "' 팀에 합류했어요. 연락 수단을 확인하세요");
        return new AcceptResponse(applicationId, ApplicationStatus.ACCEPTED, roleClosed, postClosed);
    }

    /** APPL-06 거절: 팀장만, 대기 상태만. 사유는 선택 */
    @Transactional
    public RejectResponse reject(Long userId, Long applicationId, RejectRequest request) {
        Application application = getApplication(applicationId);
        requireLeader(application.getPost(), userId);
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_APPLICATION_STATUS);
        }
        application.reject(request == null ? null : request.reason(), LocalDateTime.now());
        notificationService.notify(application.getApplicant().getId(), NotificationType.REJECTED,
                NotificationTargetType.POST, application.getPost().getId(),
                "'" + application.getPost().getTitle() + "' 지원 결과를 확인하세요");
        return new RejectResponse(applicationId, application.getStatus());
    }

    /** APPL-07 연락 수단: 팀장이거나 이 글에 수락된 지원자만 */
    public ContactResponse getContact(Long userId, Long postId) {
        Post post = getExistingPost(postId);
        boolean leader = post.getLeaderId().equals(userId);
        boolean acceptedMember = applicationRepository
                .existsByPostIdAndApplicantIdAndStatus(postId, userId, ApplicationStatus.ACCEPTED);
        if (!leader && !acceptedMember) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "수락된 지원자만 연락 수단을 볼 수 있어요");
        }
        return new ContactResponse(post.getContactLink());
    }

    /** APPL-10 내 지원 내역: 최신순, 상태로 거르기, 커서 방식 */
    public CursorPage<MyApplicationResponse> getMyApplications(Long userId, ApplicationStatus status,
            Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        List<Application> fetched = applicationRepository.findMine(userId, status, cursor,
                PageRequest.of(0, pageSize + 1));
        return CursorPage.of(fetched, pageSize, Application::getId, MyApplicationResponse::from);
    }

    // ===== 공통 확인 =====

    /** 삭제된 글은 없는 글로 취급 */
    private Post getExistingPost(Long postId) {
        return postRepository.findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모집글을 찾을 수 없어요"));
    }

    /** 지원자에게는 숨김 처리된 글도 없는 글로 취급 */
    private Post getVisiblePost(Long postId) {
        Post post = getExistingPost(postId);
        if (post.isHidden()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "모집글을 찾을 수 없어요");
        }
        return post;
    }

    private Application getApplication(Long applicationId) {
        return applicationRepository.findWithPostAndRoleById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "지원 내역을 찾을 수 없어요"));
    }

    private void requireLeader(Post post, Long userId) {
        if (!post.getLeaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "이 모집글의 팀장만 할 수 있어요");
        }
    }
}
