package com.grytonems.backend.domain.block.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 차단·해제 후 상태 */
public record BlockResponse(Long userId, @JsonProperty("isBlocked") boolean isBlocked) {
}
