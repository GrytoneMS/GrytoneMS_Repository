package com.grytonems.backend.domain.user;

/** users.status (DB 설계서 8장) */
public enum UserStatus {
    UNVERIFIED, // 미인증: 둘러보기만 가능
    VERIFIED,   // 학교 인증: 글쓰기·지원 가능
    SUSPENDED,  // 이용 정지
    WITHDRAWN   // 탈퇴
}
