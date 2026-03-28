package com.youthletter.domain.user.controller;

import com.youthletter.domain.user.dto.TokenReissueRequest;
import com.youthletter.domain.user.dto.TokenResponse;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 Access Token을 재발급합니다")
    @PostMapping("/reissue")
    public ResponseEntity<GlobalResponse<TokenResponse>> reissue(
            @Valid @RequestBody TokenReissueRequest request) {

        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        if (!jwtTokenProvider.validateRefreshToken(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "만료되었거나 유효하지 않은 Refresh Token입니다");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        return ResponseEntity.ok(
                GlobalResponse.ok(new TokenResponse(newAccessToken, newRefreshToken)));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 무효화합니다")
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        jwtTokenProvider.deleteRefreshToken(principal.getUserId());
        return ResponseEntity.ok(GlobalResponse.ok("로그아웃 되었습니다", null));
    }
}