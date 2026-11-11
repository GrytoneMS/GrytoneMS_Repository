package com.grytonems.backend.domain.post;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.application.RoleApplicationCount;
import com.grytonems.backend.domain.post.dto.MyPostResponse;
import com.grytonems.backend.global.common.CursorPage;

import lombok.RequiredArgsConstructor;

/**
 * APPL-11 내 모집글: 모집중·마감 탭, 역할별 지원자 수.
 * 숨김 글은 팀장 본인에게도 보이지 않으므로 여기서도 빠집니다 (11/11 팀 결정).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPostService {

    private final PostRepository postRepository;
    private final PostRoleRepository postRoleRepository;
    private final ApplicationRepository applicationRepository;

    public CursorPage<MyPostResponse> getMyPosts(Long userId, RecruitStatus status, Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        List<Post> fetched = postRepository.findMine(userId, status, cursor, PageRequest.of(0, pageSize + 1));
        List<Post> page = fetched.size() > pageSize ? fetched.subList(0, pageSize) : fetched;
        if (page.isEmpty()) {
            return new CursorPage<>(List.of(), null, false);
        }

        // 글 수만큼 쿼리를 날리지 않도록 역할·지원 수를 한 번에 읽습니다
        List<Long> postIds = page.stream().map(Post::getId).toList();
        Map<Long, List<PostRole>> rolesByPost = postRoleRepository.findAllByPostIdInOrderByIdAsc(postIds).stream()
                .collect(Collectors.groupingBy(role -> role.getPost().getId()));
        Map<Long, MyPostResponse.Counts> countsByRole = toCounts(applicationRepository.countByRoleAndStatus(postIds));

        LocalDate today = LocalDate.now();
        return CursorPage.of(fetched, pageSize, Post::getId, post -> MyPostResponse.of(post,
                rolesByPost.getOrDefault(post.getId(), List.of()), countsByRole, today));
    }

    /** 지원자 수 = 취소를 뺀 전부 (지원자 목록 APPL-04와 같은 기준), 대기 수 = PENDING */
    private Map<Long, MyPostResponse.Counts> toCounts(List<RoleApplicationCount> rows) {
        Map<Long, MyPostResponse.Counts> counts = new HashMap<>();
        for (RoleApplicationCount row : rows) {
            if (row.status() == ApplicationStatus.CANCELED) {
                continue;
            }
            MyPostResponse.Counts current = counts.getOrDefault(row.postRoleId(), MyPostResponse.Counts.ZERO);
            int pending = row.status() == ApplicationStatus.PENDING ? (int) row.count() : 0;
            counts.put(row.postRoleId(),
                    new MyPostResponse.Counts(current.applicants() + (int) row.count(), current.pending() + pending));
        }
        return counts;
    }
}
