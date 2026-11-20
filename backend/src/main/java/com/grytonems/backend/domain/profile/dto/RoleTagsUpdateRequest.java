package com.grytonems.backend.domain.profile.dto;

import java.util.List;

import com.grytonems.backend.domain.post.RoleCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT /api/users/me/role-tags (PROF-02). 보낸 목록으로 통째로 바꿉니다. 빈 목록이면 모두 지웁니다 */
public record RoleTagsUpdateRequest(

        @NotNull(message = "역할 목록을 보내 주세요")
        @Size(max = 3, message = "희망 역할은 최대 3개까지 고를 수 있어요")
        List<@NotNull(message = "역할 코드가 비어 있어요") RoleCode> roleCodes
) {
}
