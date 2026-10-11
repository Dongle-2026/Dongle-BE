package com.youthletter.global.auth.mobile;

import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 앱이 제공자 SDK로 받은 OIDC idToken을 검증한다.
 * 서명(JWKS, RS256) · iss · aud · exp/nbf · (보낸 경우) nonce.
 * JWKS는 디코더가 캐시한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdTokenVerifier {
    private final MobileOAuthProperties properties;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public record Identity(String provider, String subject, String email, boolean emailVerified, String name) {}

    public Identity verify(String provider, String idToken, String nonce) {
        MobileOAuthProperties.Provider config = properties.getProviders().get(provider);
        if (config == null) throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        if (config.getAudiences().stream().noneMatch(StringUtils::hasText))
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED);

        Jwt jwt;
        try {
            jwt = decoders.computeIfAbsent(provider, p -> decoder(config)).decode(idToken);
        } catch (BadJwtException e) {
            log.info("idToken 거부: provider={}, reason={}", provider, e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        } catch (JwtException e) {
            log.warn("idToken 검증 키 조회 실패: provider={}, reason={}", provider, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE);
        }
        if (nonce != null && !nonce.equals(jwt.getClaimAsString("nonce")))
            throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);
        if (!StringUtils.hasText(jwt.getSubject())) throw new BusinessException(ErrorCode.INVALID_ID_TOKEN);

        // 구글은 email_verified를 준다. 카카오 idToken의 email은 카카오가 인증한 대표 이메일이다.
        Boolean verifiedClaim = jwt.getClaimAsBoolean("email_verified");
        boolean emailVerified = provider.equals("kakao") ? jwt.hasClaim("email") : Boolean.TRUE.equals(verifiedClaim);
        String name = provider.equals("kakao") ? jwt.getClaimAsString("nickname") : jwt.getClaimAsString("name");
        return new Identity(provider, jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified, name);
    }

    private static JwtDecoder decoder(MobileOAuthProperties.Provider config) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.getJwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        List<String> audiences = config.getAudiences().stream().filter(StringUtils::hasText).toList();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                jwt -> config.getIssuers().contains(jwt.getClaimAsString("iss"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "issuer", null)),
                jwt -> jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience", null))));
        return decoder;
    }
}
