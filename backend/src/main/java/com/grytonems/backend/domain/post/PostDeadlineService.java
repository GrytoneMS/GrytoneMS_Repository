package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.notification.NotificationService;
import com.grytonems.backend.domain.notification.NotificationTargetType;
import com.grytonems.backend.domain.notification.NotificationType;
import com.grytonems.backend.domain.scrap.ScrapRepository;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;

import lombok.RequiredArgsConstructor;

/**
 * 마감일 기준 처리 (POST-06 글 자동 마감, NOTI-01 마감 D-1 알림).
 * 실행 시각은 PostDeadlineScheduler가 정하고, 여기서는 "오늘"을 받아 처리만 합니다 (테스트에서 날짜를 넘겨 호출).
 */
@Service
@RequiredArgsConstructor
public class PostDeadlineService {

    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final ScrapRepository scrapRepository;
    private final UserRepository userRepository;

    /** 마감일이 지난 모집중 글 id. 글마다 closeExpired를 따로 호출합니다 */
    @Transactional(readOnly = true)
    public List<Long> findExpiredPostIds(LocalDate today) {
        return postRepository.findExpiredIds(RecruitStatus.OPEN, today);
    }

    /**
     * POST-06: 글 하나 자동 마감 (글마다 트랜잭션을 나눠, 한 글이 실패해도 나머지는 마감됩니다).
     * 수동 마감(POST-04)과 같이 글·역할을 마감하고, 대기 지원을 마감하고 대기자에게 알립니다 (APPL-08).
     * @return 마감했으면 true, 그사이 이미 마감됐거나 조건이 바뀌었으면 false
     */
    @Transactional
    public boolean closeExpired(Long postId, LocalDate today) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getStatus() != RecruitStatus.OPEN || post.getDeletedAt() != null
                || !post.getDeadline().isBefore(today)) {
            return false;
        }

        closeWithApplicants(post);
        return true;
    }

    /**
     * 글 하나를 지금 마감합니다: 글·역할 마감, 대기 지원 마감, 대기자에게 POST_CLOSED.
     * 자동 마감(POST-06)과 탈퇴(AUTH-09)에서 씁니다. 호출하는 쪽 트랜잭션 안에서 실행됩니다.
     */
    @Transactional
    public void closeWithApplicants(Post post) {
        Long postId = post.getId();
        // 글·역할 마감을 먼저 해야 아래 일괄 변경 전에 저장됩니다
        List<PostRole> roles = postRoleRepository.findAllByPostIdOrderByIdAsc(postId);
        post.close();
        roles.forEach(PostRole::close);

        List<Long> waitingApplicantIds = new ArrayList<>();
        for (PostRole role : roles) {
            waitingApplicantIds.addAll(applicationRepository
                    .findApplicantIdsByPostRoleIdAndStatus(role.getId(), ApplicationStatus.PENDING));
            applicationRepository.closePendingByPostRoleId(role.getId(),
                    ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
        }

        notificationService.notifyAll(waitingApplicantIds, NotificationType.POST_CLOSED,
                NotificationTargetType.POST, postId, "'" + post.getTitle() + "' 모집이 마감되었어요");
    }

    /**
     * NOTI-01: 내일 마감인 모집중 글의 팀장과 그 글을 스크랩한 사람에게 알림 (하루 한 번 실행).
     * @return 보낸 알림 수
     */
    @Transactional
    public int notifyDeadlineTomorrow(LocalDate today) {
        List<Post> posts = postRepository.findAllByStatusAndDeadlineAndHiddenFalseAndDeletedAtIsNull(
                RecruitStatus.OPEN, today.plusDays(1));
        int sent = 0;
        for (Post post : posts) {
            // 팀장이 정지 중인 글은 아무에게도 보이지 않으므로 알리지 않음 (ADMN-04)
            if (isLeaderSuspended(post)) {
                continue;
            }
            notificationService.notify(post.getLeaderId(), NotificationType.DEADLINE_SOON,
                    NotificationTargetType.POST, post.getId(), "'" + post.getTitle() + "' 마감이 내일이에요");
            List<Long> scrapperIds = scrapRepository.findUserIdsByPostId(post.getId());
            notificationService.notifyAll(scrapperIds, NotificationType.SCRAP_DEADLINE_SOON,
                    NotificationTargetType.POST, post.getId(), "스크랩한 '" + post.getTitle() + "' 모집이 내일 마감돼요");
            sent += 1 + scrapperIds.size();
        }
        return sent;
    }

    private boolean isLeaderSuspended(Post post) {
        return userRepository.findById(post.getLeaderId())
                .map(leader -> leader.getStatus() == UserStatus.SUSPENDED)
                .orElse(false);
    }
}
