package com.youthletter.global.auth.mobile;

import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
public class MobileOAuthController {
    private final MobileOAuthService service;

    public record MobileLoginRequest(@NotBlank @Size(max = 8192) String idToken, @Size(max = 256) String nonce) {}

    @Operation(summary = "모바일 소셜 로그인", description = "provider=kakao|google. 앱 SDK가 받은 idToken 검증 후 JWT 발급. newUser=true면 최초 가입")
    @PostMapping("/api/auth/oauth/{provider}")
    public GlobalResponse<MobileOAuthService.LoginResult> login(@PathVariable String provider,
                                                                @Valid @RequestBody MobileLoginRequest request) {
        return GlobalResponse.ok(service.login(provider, request.idToken(), request.nonce()));
    }
}
