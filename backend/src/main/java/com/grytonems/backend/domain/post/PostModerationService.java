package com.grytonems.backend.domain.post;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 모집글 숨김·복구 (SAFE-02 신고 누적 자동 숨김, ADMN-02 관리자 조치, ADMN-04 정지 시 숨김에서 공통 사용)
 *
 * 팀 결정 (11/11):
 * - 숨김 글은 팀장 본인에게도 보이지 않습니다 (목록·상세 404)
 * - 숨김 시 팀장에게 알림, 복구 시 다시 알림 (명세 알림 목록에 없는 1차 추가)
 * - 숨김 시 대기 중이던 지원은 마감 처리하고 지원자에게 마감 알림
 * - 모집 상태(OPEN/CLOSED)는 바꾸지 않아서, 복구하면 숨김 전 상태로 돌아갑니다
 *
 * 신고 기능을 만들 때는 신고 3건이 쌓이면 hide(postId)를 호출하면 됩니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PostModerationService {

    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    /** 숨김: 글 숨김 + 대기 지원 마감 + 팀장·대기자 알림 */
    public void hide(Long postId) {
        Post post = getExistingPost(postId);
        if (post.isHidden()) {
            throw new BusinessException(ErrorCode.POST_ALREADY_HIDDEN);
        }
        post.hide();

        // 역할마다 대기 중인 지원자를 기억하고 그 지원들을 마감
        List<Long> waitingApplicantIds = new ArrayList<>();
        for (PostRole role : postRoleRepository.findAllByPostIdOrderByIdAsc(postId)) {
            waitingApplicantIds.addAll(applicationRepository
                    .findApplicantIdsByPostRoleIdAndStatus(role.getId(), ApplicationStatus.PENDING));
            applicationRepository.closePendingByPostRoleId(role.getId(),
                    ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
        }

        // 숨김 글은 열 수 없으므로 이동 대상 없이 문구만 보냅니다. 신고자 정보는 넣지 않습니다
        notificationService.notify(post.getLeaderId(), NotificationType.POST_HIDDEN, null, null,
                "'" + post.getTitle() + "' 모집글이 운영 정책에 따라 숨김 처리됐어요");
        notificationService.notifyAll(waitingApplicantIds, NotificationType.POST_CLOSED, null, null,
                "'" + post.getTitle() + "' 모집이 마감되었어요");
    }

    /** 복구: 숨김 해제 + 팀장 알림. 마감된 지원은 되돌리지 않습니다 */
    public void restore(Long postId) {
        Post post = getExistingPost(postId);
        if (!post.isHidden()) {
            throw new BusinessException(ErrorCode.POST_NOT_HIDDEN);
        }
        post.restore();

        notificationService.notify(post.getLeaderId(), NotificationType.POST_RESTORED,
                NotificationTargetType.POST, postId,
                "'" + post.getTitle() + "' 모집글이 운영자 확인 후 다시 공개됐어요");
    }

    /** 삭제된 글은 없는 글로 취급 */
    private Post getExistingPost(Long postId) {
        return postRepository.findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모집글을 찾을 수 없어요"));
    }
}
