package com.grytonems.backend.domain.block;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 차단 관계를 다른 기능에서 확인할 때 쓰는 곳 (SAFE-03, LIST-06).
 * 내가 차단했든 상대가 나를 차단했든 "차단 관계"로 보고 서로의 글·지원·프로필을 가립니다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockPolicy {

    private final BlockRepository blockRepository;

    /** 목록에서 뺄 사람: 내가 차단한 사람 + 나를 차단한 사람. 비회원이면 비어 있음 */
    public Set<Long> relatedUserIds(Long viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>(blockRepository.findBlockedIds(viewerId));
        ids.addAll(blockRepository.findBlockerIds(viewerId));
        return ids;
    }

    /**
     * 상대(글의 팀장, 프로필 주인)의 것을 볼 수 있는지 확인.
     * 내가 차단했으면 BLOCKED_USER(403, 차단 해제 안내), 상대가 나를 차단했으면 NOT_FOUND(404, 차단당한 걸 드러내지 않음)
     */
    public void checkVisible(Long viewerId, Long ownerId, String notFoundMessage) {
        if (viewerId == null || ownerId == null || viewerId.equals(ownerId)) {
            return;
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(viewerId, ownerId)) {
            throw new BusinessException(ErrorCode.BLOCKED_USER);
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(ownerId, viewerId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, notFoundMessage);
        }
    }
}
