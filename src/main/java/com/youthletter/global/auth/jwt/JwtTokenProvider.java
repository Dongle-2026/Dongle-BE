package com.youthletter.global.auth.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String REFRESH_PREFIX   = "refresh:";
    private static final String BLACKLIST_PREFIX  = "blacklist:";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey secretKey;

    private final RedisTemplate<String, Object> redisTemplate;

    public JwtTokenProvider(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    protected void init() {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ── 토큰 생성 ─────────────────────────────────────────────

    public String createAccessToken(Long userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /** 하위 호환용 — role 없이 호출 시 기본값 'USER' */
    public String createAccessToken(Long userId, String email) {
        return createAccessToken(userId, email, "USER");
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        String refreshToken = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();

        redisTemplate.opsForValue().set(
                REFRESH_PREFIX + userId,
                refreshToken,
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );
        return refreshToken;
    }

    // ── 인증 객체 생성 ────────────────────────────────────────

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.parseLong(claims.getSubject());
        String email = claims.get("email", String.class);
        String role  = claims.get("role",  String.class);
        if (role == null) role = "USER";

        CustomUserPrincipal principal = new CustomUserPrincipal(userId, email);
        return new UsernamePasswordAuthenticationToken(
                principal, "",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // ── 토큰 검증 ─────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰");
        } catch (JwtException e) {
            log.warn("유효하지 않은 JWT 토큰: {}", e.getMessage());
        }
        return false;
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    // ── Refresh Token ─────────────────────────────────────────

    public boolean validateRefreshToken(Long userId, String refreshToken) {
        String stored = (String) redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        return refreshToken.equals(stored);
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(REFRESH_PREFIX + userId);
    }

    // ── Access Token 블랙리스트 ────────────────────────────────

    /**
     * 로그아웃된 AT를 Redis 블랙리스트에 등록 (남은 유효 시간만큼 TTL)
     */
    public void blacklistAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + token,
                        "logout",
                        remaining,
                        TimeUnit.MILLISECONDS
                );
            }
        } catch (Exception e) {
            log.warn("AT 블랙리스트 등록 실패 (이미 만료된 토큰일 수 있음): {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
    }

    // ── 내부 ──────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
