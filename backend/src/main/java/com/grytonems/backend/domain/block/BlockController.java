package com.grytonems.backend.domain.block;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.block.dto.BlockRequest;
import com.grytonems.backend.domain.block.dto.BlockResponse;
import com.grytonems.backend.domain.block.dto.BlockedUserResponse;
import com.grytonems.backend.global.common.ApiResponse;
import com.grytonems.backend.global.common.CursorPage;
import com.grytonems.backend.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** SAFE-03 차단 (로그인만 하면 가능) */
@RestController
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/api/blocks")
    public ApiResponse<BlockResponse> block(@AuthenticationPrincipal AuthUser loginUser,
            @Valid @RequestBody BlockRequest request) {
        return ApiResponse.ok(blockService.block(loginUser.id(), request.userId()));
    }

    @DeleteMapping("/api/blocks/{userId}")
    public ApiResponse<BlockResponse> unblock(@AuthenticationPrincipal AuthUser loginUser, @PathVariable Long userId) {
        return ApiResponse.ok(blockService.unblock(loginUser.id(), userId));
    }

    @GetMapping("/api/users/me/blocks")
    public ApiResponse<CursorPage<BlockedUserResponse>> getMyBlocks(@AuthenticationPrincipal AuthUser loginUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(blockService.getMyBlocks(loginUser.id(), cursor, size));
    }
}
