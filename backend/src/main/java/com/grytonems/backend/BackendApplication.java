package com.grytonems.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 로그인은 JWT로 직접 처리하므로 스프링 기본 계정(콘솔에 찍히는 임시 비밀번호)은 끕니다
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BackendApplication {

    public static void main(String[] args) {
        // 모든 시각은 KST 기준 (기능 명세서 공통 정책)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(BackendApplication.class, args);
    }
}
