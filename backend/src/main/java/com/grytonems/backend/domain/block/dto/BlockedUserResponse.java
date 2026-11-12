package com.grytonems.backend.domain.block.dto;

import java.time.LocalDateTime;

import com.grytonems.backend.domain.block.Block;
import com.grytonems.backend.domain.user.User;

/** GET /api/users/me/blocks 항목 */
public record BlockedUserResponse(Long userId, String nickname, String schoolName, LocalDateTime blockedAt) {

    public static BlockedUserResponse from(Block block) {
        User user = block.getBlocked();
        String schoolName = user.getSchool() == null ? null : user.getSchool().getName();
        return new BlockedUserResponse(user.getId(), user.getNickname(), schoolName, block.getCreatedAt());
    }
}
