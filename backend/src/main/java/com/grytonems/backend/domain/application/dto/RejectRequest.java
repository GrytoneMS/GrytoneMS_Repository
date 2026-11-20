package com.grytonems.backend.domain.application.dto;

import com.grytonems.backend.domain.application.RejectReason;

/** PATCH /api/applications/{id}/reject (APPL-06). 사유는 선택 입력 */
public record RejectRequest(RejectReason reason) {
}
