package com.grytonems.backend.domain.school;

import com.grytonems.backend.global.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [1] Entity: DB 테이블 한 행을 나타냅니다. (DB 설계서 4장 schools)
 * - 컬럼 이름·길이·NOT NULL·UNIQUE를 설계서와 똑같이 맞춥니다.
 * - setter는 열지 않고, 값을 바꾸는 메서드를 따로 만듭니다 (아래 deactivate).
 */
@Getter
@Entity
@Table(name = "schools")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 30)
    private String campus;

    @Column(name = "email_domain", nullable = false, unique = true, length = 100)
    private String emailDomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Region region;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private School(String name, String campus, String emailDomain, Region region) {
        this.name = name;
        this.campus = campus;
        this.emailDomain = emailDomain;
        this.region = region;
        this.active = true;
    }

    /** ADMN-05: 비활성화하면 신규 인증 불가 */
    public void deactivate() {
        this.active = false;
    }
}
