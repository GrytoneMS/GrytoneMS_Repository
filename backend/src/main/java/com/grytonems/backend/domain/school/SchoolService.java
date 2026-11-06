package com.grytonems.backend.domain.school;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grytonems.backend.domain.school.dto.SchoolCreateRequest;
import com.grytonems.backend.domain.school.dto.SchoolResponse;
import com.grytonems.backend.global.error.BusinessException;
import com.grytonems.backend.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * [4] Service: 기능 명세서의 규칙과 예외 처리가 들어가는 곳.
 * - 조회만 하면 @Transactional(readOnly = true), 저장·수정이면 @Transactional
 * - 규칙 위반은 BusinessException 으로 던집니다 (응답 변환은 핸들러가 함)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolService {

    private final SchoolRepository schoolRepository;

    /** AUTH-06: 학교 인증 화면의 학교 선택 목록 (활성 학교만) */
    public List<SchoolResponse> getActiveSchools() {
        return schoolRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(SchoolResponse::from)
                .toList();
    }

    /** ADMN-05: 학교 등록 (이메일 도메인 중복 불가) */
    @Transactional
    public SchoolResponse createSchool(SchoolCreateRequest request) {
        if (schoolRepository.existsByEmailDomain(request.emailDomain())) {
            throw new BusinessException(ErrorCode.SCHOOL_DOMAIN_DUPLICATED);
        }
        School saved = schoolRepository.save(request.toEntity());
        return SchoolResponse.from(saved);
    }

    /** ADMN-05: 학교 비활성화 */
    @Transactional
    public void deactivateSchool(Long schoolId) {
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHOOL_NOT_FOUND));
        school.deactivate(); // 트랜잭션이 끝나면 변경 내용이 자동으로 DB에 반영됩니다
    }
}
