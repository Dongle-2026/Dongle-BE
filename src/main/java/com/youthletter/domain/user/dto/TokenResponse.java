package com.youthletter.domain.user.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}