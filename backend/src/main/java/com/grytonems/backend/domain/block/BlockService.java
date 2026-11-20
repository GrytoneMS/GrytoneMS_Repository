package com.grytonems.backend.domain.block;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.application.ApplicationRepository;
import com.grytonems.backend.domain.application.ApplicationStatus;
import com.grytonems.backend.domain.block.dto.BlockResponse;
import com.grytonems.backend.domain.block.dto.BlockedUserResponse;
import com.grytonems.backend.domain.user.User;
import com.grytonems.backend.domain.user.UserRepository;
import com.grytonems.backend.domain.user.UserStatus;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * SAFE-03 차단·해제·차단 목록.
 * 차단·해제는 여러 번 보내도 결과가 같습니다.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;

    /**
     * 차단. 둘 사이의 대기 지원(양쪽 방향)은 알림 없이 마감합니다. 이미 수락된 팀은 그대로입니다.
     * 이미 차단했으면 그대로 성공. 같은 순간 두 번 눌러 UQ에 걸린 드문 경우만 ALREADY_BLOCKED(409)입니다.
     */
    @Transactional
    public BlockResponse block(Long blockerId, Long targetId) {
        if (blockerId.equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_SELF);
        }
        User target = userRepository.findById(targetId)
                .filter(user -> user.getStatus() != UserStatus.WITHDRAWN)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, targetId)) {
            return new BlockResponse(targetId, true);
        }
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        try {
            blockRepository.saveAndFlush(new Block(blocker, target));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
        applicationRepository.closePendingBetween(blockerId, targetId,
                ApplicationStatus.PENDING, ApplicationStatus.CLOSED);
        return new BlockResponse(targetId, true);
    }

    /** 차단 해제. 차단하지 않았어도 성공. 마감된 지원은 되돌리지 않습니다 */
    @Transactional
    public BlockResponse unblock(Long blockerId, Long targetId) {
        blockRepository.deleteByBlockerIdAndBlockedId(blockerId, targetId);
        return new BlockResponse(targetId, false);
    }

    @Transactional(readOnly = true)
    public CursorPage<BlockedUserResponse> getMyBlocks(Long userId, Long cursor, Integer size) {
        int pageSize = CursorPage.normalizeSize(size);
        List<Block> fetched = blockRepository.findMine(userId, cursor, PageRequest.of(0, pageSize + 1));
        return CursorPage.of(fetched, pageSize, Block::getId, BlockedUserResponse::from);
    }
}
