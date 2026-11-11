package com.grytonems.backend.domain.school;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.grytonems.backend.domain.school.dto.SchoolCreateRequest;
import com.grytonems.backend.domain.school.dto.SchoolResponse;
import com.grytonems.backend.global.common.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * [5] Controller: URL과 요청·응답만 담당. 로직은 Service에 둡니다.
 * - 요청 본문에는 @Valid 를 붙여야 DTO의 검증 규칙이 동작합니다.
 * - 응답은 항상 ApiResponse.ok(...) 로 감쌉니다.
 */
@RestController
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    /** 학교 목록 (비회원도 조회 가능) */
    @GetMapping("/api/schools")
    public ApiResponse<List<SchoolResponse>> getSchools() {
        return ApiResponse.ok(schoolService.getActiveSchools());
    }

    // /api/admin/** 은 SecurityConfig에서 관리자만 접근하도록 막혀 있습니다

    /** 관리자: 학교 등록 */
    @PostMapping("/api/admin/schools")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SchoolResponse> createSchool(@Valid @RequestBody SchoolCreateRequest request) {
        return ApiResponse.ok(schoolService.createSchool(request));
    }

    /** 관리자: 학교 비활성화 */
    @PatchMapping("/api/admin/schools/{schoolId}/deactivate")
    public ApiResponse<Void> deactivateSchool(@PathVariable Long schoolId) {
        schoolService.deactivateSchool(schoolId);
        return ApiResponse.ok();
    }
}
