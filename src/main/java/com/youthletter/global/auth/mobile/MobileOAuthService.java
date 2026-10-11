package com.youthletter.global.auth.mobile;

import com.youthletter.domain.user.entity.OAuthAccount;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.OAuthAccountRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * 모바일 idToken 로그인/가입.
 * - 같은 제공자 계정(sub)은 같은 사용자로 로그인.
 * - 신규인데 이메일이 기존 활성 계정과 같으면 연결하지 않고 409로 거부한다(계정 탈취 방지).
 * - 탈퇴한 사용자의 제공자 계정으로 다시 로그인하면 기존 연결을 정리하고 새 사용자로 가입한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileOAuthService {
    private final IdTokenVerifier verifier;
    private final UserRepository userRepository;
    private final OAuthAccountRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbc;

    public record LoginResult(String accessToken, String refreshToken, String nickname, boolean newUser) {}

    @Transactional
    public LoginResult login(String provider, String idToken, String nonce) {
        provider = provider.toLowerCase(Locale.ROOT);
        IdTokenVerifier.Identity identity = verifier.verify(provider, idToken, nonce);
        String providerKey = provider.toUpperCase(Locale.ROOT);
        // 같은 제공자 계정의 동시 최초 로그인이 중복 가입되지 않도록 직렬화한다.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, "oauth:" + providerKey + ":" + identity.subject());

        var linkedUserId = accountRepository.findUserIdByProviderAndProviderId(providerKey, identity.subject());
        if (linkedUserId.isPresent()) {
            var active = userRepository.findById(linkedUserId.get()).filter(u -> u.getDeletedAt() == null);
            if (active.isPresent()) return issue(active.get(), false);
            accountRepository.deleteByProviderAndProviderId(providerKey, identity.subject()); // 탈퇴 사용자
        }

        String email = identity.emailVerified() && StringUtils.hasText(identity.email()) ? identity.email() : null;
        if (email != null && userRepository.findByEmail(email).isPresent())
            throw new BusinessException(ErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED);
        if (email == null || userRepository.existsByEmail(email))
            email = provider + "_" + identity.subject() + "@oauth.local"; // 이메일 미제공/탈퇴 계정이 점유

        String name = truncate(StringUtils.hasText(identity.name()) ? identity.name().strip() : "돈글 회원", 50);
        User user = userRepository.save(User.builder()
                .email(email)
                .name(name)
                .nickname(uniqueNickname(truncate(name, 20)))
                .build());
        accountRepository.save(OAuthAccount.builder().user(user).provider(providerKey).providerId(identity.subject()).build());
        log.info("모바일 소셜 가입: provider={}, userId={}", providerKey, user.getId());
        return issue(user, true);
    }

    private LoginResult issue(User user, boolean newUser) {
        return new LoginResult(
                jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole()),
                jwtTokenProvider.createRefreshToken(user.getId()),
                user.getNickname(),
                newUser);
    }

    private String uniqueNickname(String base) {
        if (base.length() >= 2 && !userRepository.existsByNickname(base)) return base;
        for (int i = 0; i < 10; i++) {
            String candidate = base + "_" + UUID.randomUUID().toString().substring(0, 6);
            if (!userRepository.existsByNickname(candidate)) return candidate;
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
