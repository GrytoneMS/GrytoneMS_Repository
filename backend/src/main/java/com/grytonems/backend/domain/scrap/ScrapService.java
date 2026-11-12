package com.grytonems.backend.domain.scrap;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.block.BlockPolicy;
import com.grytonems.backend.domain.post.Post;
import com.grytonems.backend.domain.post.PostRepository;
import com.grytonems.backend.domain.post.PostRole;
import com.grytonems.backend.domain.post.PostRoleRepository;
import com.grytonems.backend.domain.post.dto.PostCardResponse;
import com.grytonems.backend.domain.scrap.dto.MyScrapResponse;
import com.grytonems.backend.domain.scrap.dto.ScrapResponse;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * SCRP-01 스크랩·해제, SCRP-02 내 스크랩 목록.
 * 스크랩·해제는 여러 번 눌러도 결과가 같습니다 (이미 스크랩한 글을 또 스크랩해도 성공).
 */
@Service
@RequiredArgsConstructor
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final UserRepository userRepository;
    private final BlockPolicy blockPolicy;

    /**
     * 스크랩. 본인 글은 불가, 삭제·숨김 글은 404.
     * 트랜잭션을 걸지 않습니다: 동시에 두 번 눌러 UQ에 걸리면 저장만 실패하고 "이미 스크랩됨"으로 성공 처리합니다.
     */
    public ScrapResponse scrap(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .filter(p -> p.getDeletedAt() == null && !p.isHidden())
                .filter(p -> userRepository.findById(p.getLeaderId())
                        .map(leader -> leader.getStatus() != UserStatus.SUSPENDED).orElse(true))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "모집글을 찾을 수 없어요"));
        if (post.getLeaderId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_SCRAP_OWN_POST);
        }
        if (!scrapRepository.existsByUserIdAndPostId(userId, postId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            try {
                scrapRepository.save(new Scrap(user, post));
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 먼저 저장함: 결과는 같으므로 성공
            }
        }
        return new ScrapResponse(postId, true);
    }

    /** 스크랩 해제. 스크랩하지 않았거나 글이 숨김·삭제돼도 성공 */
    @Transactional
    public ScrapResponse unscrap(Long userId, Long postId) {
        scrapRepository.deleteByUserIdAndPostId(userId, postId);
        return new ScrapResponse(postId, false);
    }

    /** SCRP-02: 최신 스크랩순. 카드는 GET /api/posts 와 같은 모양 */
    @Transactional(readOnly = true)
    public CursorPage<MyScrapResponse> getMyScraps(Long userId, Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        // 차단 관계인 사람의 글은 빼기 (빈 목록은 쿼리에 넣을 수 없어 없는 id -1을 넣음)
        Set<Long> excluded = new HashSet<>(blockPolicy.relatedUserIds(userId));
        excluded.add(-1L);
        List<Scrap> fetched = scrapRepository.findMine(userId, excluded, cursor, PageRequest.of(0, pageSize + 1));
        List<Scrap> page = fetched.size() > pageSize ? fetched.subList(0, pageSize) : fetched;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        List<Long> postIds = page.stream().map(scrap -> scrap.getPost().getId()).toList();
        Map<Long, List<PostRole>> rolesByPost = postRoleRepository.findAllByPostIdInOrderByIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(role -> role.getPost().getId()));
        List<Long> leaderIds = page.stream().map(scrap -> scrap.getPost().getLeaderId()).distinct().toList();
        Map<Long, User> leaders = userRepository.findAllWithSchoolByIdIn(leaderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        LocalDate today = LocalDate.now();
        return CursorPage.of(fetched, pageSize, Scrap::getId, scrap -> {
            Post post = scrap.getPost();
            PostCardResponse card = PostCardResponse.of(post, rolesByPost.getOrDefault(post.getId(), List.of()),
                    leaders.get(post.getLeaderId()), today, true);
            return new MyScrapResponse(scrap.getId(), scrap.getCreatedAt(), card);
        });
    }
}
