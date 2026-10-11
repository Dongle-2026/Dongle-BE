package com.youthletter.global.auth.mobile;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import com.youthletter.global.auth.oauth.SocialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 모바일 idToken 로그인/가입. idToken 검증 후 계정 연결 규칙은 SocialAccountService(웹과 공통).
 */
@Service
@RequiredArgsConstructor
public class MobileOAuthService {
    private final IdTokenVerifier verifier;
    private final SocialAccountService socialAccounts;
    private final JwtTokenProvider jwtTokenProvider;

    public record LoginResult(String accessToken, String refreshToken, String nickname, boolean newUser) {}

    @Transactional
    public LoginResult login(String provider, String idToken, String nonce) {
        provider = provider.toLowerCase(Locale.ROOT);
        IdTokenVerifier.Identity identity = verifier.verify(provider, idToken, nonce);
        var resolved = socialAccounts.resolve(provider, identity.subject(), identity.email(), identity.emailVerified(), identity.name());
        return issue(resolved.user(), resolved.newUser());
    }

    private LoginResult issue(User user, boolean newUser) {
        return new LoginResult(
                jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getId()),
                user.getNickname(),
                newUser);
    }
}
