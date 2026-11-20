package com.grytonems.backend.global.file;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/** 프로필 이미지만 /files/profiles/** 로 공개합니다. 인증 서류(documents)는 공개하지 않습니다 */
@Configuration
@RequiredArgsConstructor
public class FileWebConfig implements WebMvcConfigurer {

    private final FileStorage fileStorage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = fileStorage.root().resolve("profiles").toUri().toString();
        registry.addResourceHandler("/files/profiles/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
