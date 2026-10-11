package com.youthletter.global.auth.oauth;

import com.youthletter.domain.user.entity.OAuthAccount;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.OAuthAccountRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 소셜 계정 → 서비스 사용자 연결 규칙(웹 OAuth2와 모바일 idToken 공통).
 * - 같은 제공자 계정(sub)은 같은 사용자.
 * - 신규인데 이메일이 기존 활성 계정과 같으면 연결하지 않고 거부하며, 기존 가입 방법을 안내한다(계정 탈취 방지).
 * - 탈퇴한 사용자의 제공자 계정이면 기존 연결을 정리하고 새 사용자로 가입한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAccountService {
    private static final Map<String, String> PROVIDER_NAMES = Map.of("KAKAO", "카카오", "GOOGLE", "구글");
    private final UserRepository userRepository;
    private final OAuthAccountRepository accountRepository;
    private final JdbcTemplate jdbc;

    public record Resolved(User user, boolean newUser) {}

    /** @param provider kakao | google (대소문자 무관) */
    @Transactional
    public Resolved resolve(String provider, String subject, String email, boolean emailVerified, String name) {
        String providerKey = provider.toUpperCase(Locale.ROOT);
        // 같은 제공자 계정의 동시 최초 로그인이 중복 가입되지 않도록 직렬화한다.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {}, "oauth:" + providerKey + ":" + subject);

        var linkedUserId = accountRepository.findUserIdByProviderAndProviderId(providerKey, subject);
        if (linkedUserId.isPresent()) {
            var active = userRepository.findById(linkedUserId.get()).filter(u -> u.getDeletedAt() == null);
            if (active.isPresent()) return new Resolved(active.get(), false);
            accountRepository.deleteByProviderAndProviderId(providerKey, subject); // 탈퇴 사용자
        }

        String verifiedEmail = emailVerified && StringUtils.hasText(email) ? email : null;
        if (verifiedEmail != null) {
            var existing = userRepository.findByEmail(verifiedEmail);
            if (existing.isPresent()) throw new BusinessException(ErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED, guidance(existing.get()));
        }
        String storedEmail = verifiedEmail == null || userRepository.existsByEmail(verifiedEmail)
                ? provider.toLowerCase(Locale.ROOT) + "_" + subject + "@oauth.local" // 이메일 미제공/탈퇴 계정이 점유
                : verifiedEmail;

        String displayName = truncate(StringUtils.hasText(name) ? name.strip() : "돈글 회원", 50);
        User user = userRepository.save(User.builder()
                .email(storedEmail)
                .name(displayName)
                .nickname(uniqueNickname(truncate(displayName, 20)))
                .build());
        accountRepository.save(OAuthAccount.builder().user(user).provider(providerKey).providerId(subject).build());
        log.info("소셜 가입: provider={}, userId={}", providerKey, user.getId());
        return new Resolved(user, true);
    }

    /** 기존 계정의 가입 방법을 알려 다른 방법으로 로그인하도록 안내한다. */
    private String guidance(User existing) {
        var providers = accountRepository.findProvidersByUserId(existing.getId());
        if (!providers.isEmpty()) {
            String method = PROVIDER_NAMES.getOrDefault(providers.get(0), providers.get(0));
            return "이미 " + method + " 로그인으로 가입된 이메일입니다. " + method + " 로그인을 이용해 주세요";
        }
        return "이미 이메일로 가입된 계정입니다. 이메일 로그인을 이용해 주세요";
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
