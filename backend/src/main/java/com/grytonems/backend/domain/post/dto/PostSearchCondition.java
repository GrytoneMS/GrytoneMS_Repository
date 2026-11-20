package com.grytonems.backend.domain.post.dto;

import java.util.List;

import com.grytonems.backend.domain.post.LocationCode;
import com.grytonems.backend.domain.post.MeetingType;
import com.grytonems.backend.domain.post.PostCategory;
import com.grytonems.backend.domain.post.PostPeriod;
import com.grytonems.backend.domain.post.RoleCode;

/**
 * GET /api/posts 쿼리 묶음 (LIST-01~04)
 * 값이 없으면(null·빈 목록) 그 조건은 적용하지 않습니다.
 */
public record PostSearchCondition(
        String sort,
        String cursor,
        Integer size,
        List<PostCategory> categories,
        List<RoleCode> roleCodes,
        List<Long> schoolIds,
        List<LocationCode> locationCodes,
        List<MeetingType> meetingTypes,
        PostPeriod period,
        Boolean openOnly,
        String keyword
) {
}