package com.youthletter.domain.user.controller;

import com.youthletter.domain.user.dto.LoginRequest;
import com.youthletter.domain.user.dto.SignupRequest;
import com.youthletter.domain.user.dto.TokenReissueRequest;
import com.youthletter.domain.user.dto.TokenResponse;
import com.youthletter.domain.user.service.AuthService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "이메일 회원가입", description = "이메일+비밀번호로 회원가입, JWT 발급")
    @PostMapping("/signup")
    public ResponseEntity<GlobalResponse<TokenResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalResponse.created(authService.signup(request)));
    }

    @Operation(summary = "이메일 로그인", description = "이메일+비밀번호 검증, JWT 발급")
    @PostMapping("/login")
    public ResponseEntity<GlobalResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(GlobalResponse.ok(authService.login(request)));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token → 새 Access/Refresh Token 발급")
    @PostMapping("/reissue")
    public ResponseEntity<GlobalResponse<TokenResponse>> reissue(
            @Valid @RequestBody TokenReissueRequest request) {
        return ResponseEntity.ok(GlobalResponse.ok(authService.reissue(request.refreshToken())));
    }

    @Operation(summary = "로그아웃", description = "Access Token 블랙리스트 등록 + Refresh Token 삭제")
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            HttpServletRequest request) {
        authService.logout(principal.getUserId(), resolveToken(request));
        return ResponseEntity.ok(GlobalResponse.ok("로그아웃 되었습니다", null));
    }

    @Operation(summary = "회원 탈퇴", description = "soft delete + 토큰 전체 무효화")
    @DeleteMapping("/withdraw")
    public ResponseEntity<GlobalResponse<Void>> withdraw(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            HttpServletRequest request) {
        authService.withdraw(principal.getUserId(), resolveToken(request));
        return ResponseEntity.ok(GlobalResponse.ok("회원 탈퇴가 완료되었습니다", null));
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
