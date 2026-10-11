package com.youthletter.domain.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 프로필.
 * oauthProvider: kakao | google, 이메일 가입(LOCAL) 계정은 null.
 * profileImageUrl: 사진이 없으면 null (앱은 avatarId로 기본 아바타 표시).
 */
public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String nickname,
        LocalDate birthDate,
        String gender,
        String regionCode,
        String occupation,
        String incomeLevel,
        List<String> interests,
        String bio,
        String avatarId,
        String profileImageUrl,
        String oauthProvider,
        LocalDateTime joinedAt
) {
}
