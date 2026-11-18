package com.grytonems.backend.domain.school;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 천안·아산 대학 기본 목록. 서버가 켜질 때 같은 이름의 학교가 없을 때만 넣습니다.
 * 이미 있는 학교(관리자가 수정·비활성화한 학교 포함)는 건드리지 않습니다.
 * 도메인은 팀에서 실제 학생 메일로 확인한 값입니다 (남서울대는 교직원 메일로 확인).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SchoolSeeder implements ApplicationRunner {

    private record Seed(String name, String campus, String emailDomain, Region region) {
    }

    private static final List<Seed> SCHOOLS = List.of(
            new Seed("단국대학교", "천안캠퍼스", "dankook.ac.kr", Region.CHEONAN),
            new Seed("상명대학교", "천안캠퍼스", "sangmyung.kr", Region.CHEONAN),
            new Seed("백석대학교", null, "bu.ac.kr", Region.CHEONAN),
            new Seed("나사렛대학교", null, "office.kornu.ac.kr", Region.CHEONAN),
            new Seed("한국기술교육대학교", null, "koreatech.ac.kr", Region.CHEONAN),
            new Seed("남서울대학교", null, "nsu.ac.kr", Region.CHEONAN),
            new Seed("선문대학교", null, "sunmoon.ac.kr", Region.ASAN),
            new Seed("호서대학교", null, "vision.hoseo.edu,365.hoseo.edu", Region.ASAN),
            new Seed("순천향대학교", null, "sch.ac.kr", Region.ASAN));

    private final SchoolRepository schoolRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int added = 0;
        for (Seed seed : SCHOOLS) {
            if (schoolRepository.existsByName(seed.name()) || schoolRepository.existsByEmailDomain(seed.emailDomain())) {
                continue;
            }
            schoolRepository.save(School.builder()
                    .name(seed.name()).campus(seed.campus()).emailDomain(seed.emailDomain()).region(seed.region())
                    .build());
            added++;
        }
        if (added > 0) {
            log.info("기본 학교 {}곳을 등록했습니다", added);
        }
    }
}
