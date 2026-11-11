package com.grytonems.backend.domain.post;

/** post_roles.role_code, user_role_tags.role_code 허용값 (DB 설계서 8장) */
public enum RoleCode {
    PLANNING,
    DESIGN,
    DEV,
    VIDEO,
    MARKETING,
    PRESENT,
    ETC
}//(기획, 디자인, 개발, 영상, 마케팅, 발표, 기타 순)
