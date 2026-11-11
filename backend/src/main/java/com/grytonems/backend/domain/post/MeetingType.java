package com.grytonems.backend.domain.post;

/** posts.meeting_type 허용값 (DB 설계서 8장) */
public enum MeetingType {
    OFFLINE,
    ONLINE,
    HYBRID
} //(대면, 온라인, 혼합 순)
