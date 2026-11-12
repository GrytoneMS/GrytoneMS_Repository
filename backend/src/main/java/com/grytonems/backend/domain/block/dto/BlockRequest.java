package com.grytonems.backend.domain.block.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/blocks */
public record BlockRequest(@NotNull(message = "차단할 사용자를 알 수 없어요") Long userId) {
}
