package com.youthletter.domain.user.service;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/** 프로필 사진 공개 URL. 요청 중이면 요청 호스트 기준 절대 URL, 아니면 경로만. */
final class ProfileImageUrls {
    static final String PATH = "/api/profile-images/";

    private ProfileImageUrls() {}

    static String of(UUID key) {
        if (RequestContextHolder.getRequestAttributes() == null) return PATH + key;
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(PATH + key).toUriString();
    }
}
