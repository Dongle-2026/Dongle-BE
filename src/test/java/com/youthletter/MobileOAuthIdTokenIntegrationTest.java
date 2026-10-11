package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A1 모바일 소셜 로그인(idToken). 로컬 JWKS 서버로 실제 RS256 서명 검증 경로를 탄다. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class MobileOAuthIdTokenIntegrationTest {
    static final String GOOGLE_AUD = "test-google-ios-client";
    static final RSAKey KEY = generate("google-key");
    static final RSAKey OTHER_KEY = generate("attacker-key");
    static final HttpServer JWKS = start();

    static RSAKey generate(String kid) {
        try { return new RSAKeyGenerator(2048).keyID(kid).generate(); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", ex -> { ex.sendResponseHeaders(200, body.length); ex.getResponseBody().write(body); ex.close(); });
            server.start();
            return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @AfterAll static void stop() { JWKS.stop(0); }

    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("mobile-oauth.providers.google.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        r.add("mobile-oauth.providers.google.audiences", () -> GOOGLE_AUD + ",test-google-android");
        r.add("mobile-oauth.providers.kakao.audiences", () -> "");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    String sub;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        sub = "g-" + UUID.randomUUID();
    }

    String token(RSAKey key, Consumer<JWTClaimsSet.Builder> customize) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com").audience(GOOGLE_AUD).subject(sub)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", sub + "@gmail.test").claim("email_verified", true).claim("name", "구글사용자");
        customize.accept(claims);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    String token(Consumer<JWTClaimsSet.Builder> customize) throws Exception { return token(KEY, customize); }
    ResultActions login(String provider, String idToken, String nonce) throws Exception {
        Map<String, String> body = nonce == null ? Map.of("idToken", idToken) : Map.of("idToken", idToken, "nonce", nonce);
        return mvc.perform(post("/api/auth/oauth/" + provider).contentType("application/json").content(json.writeValueAsString(body)));
    }
    int accounts() { return jdbc.queryForObject("SELECT count(*) FROM oauth_accounts WHERE provider_id=?", Integer.class, sub); }

    @Test void firstLoginSignsUpAndSecondLoginReturnsSameUser() throws Exception {
        JsonNode first = json.readTree(login("google", token(c -> c.claim("nonce", "n-1")), "n-1")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.newUser").value(true))
                .andReturn().getResponse().getContentAsString()).get("data");
        em.flush();
        Long userId = jdbc.queryForObject("SELECT user_id FROM oauth_accounts WHERE provider='GOOGLE' AND provider_id=?", Long.class, sub);
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId)).isEqualTo(sub + "@gmail.test");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + first.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(userId));
        assertThat(first.get("refreshToken").asText()).isNotBlank();

        login("GOOGLE", token(c -> {}), null) // 대소문자 무관, nonce 미사용
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.newUser").value(false))
                .andExpect(jsonPath("$.data.nickname").value(first.get("nickname").asText()));
        assertThat(accounts()).isEqualTo(1);
    }

    @Test void rejectsForgedExpiredWrongAudienceIssuerAndNonce() throws Exception {
        login("google", token(OTHER_KEY, c -> {}), null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(4006));
        login("google", token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(3600)))), null).andExpect(status().isUnauthorized());
        login("google", token(c -> c.audience("someone-elses-app")), null).andExpect(status().isUnauthorized());
        login("google", token(c -> c.issuer("https://evil.example")), null).andExpect(status().isUnauthorized());
        login("google", token(c -> c.claim("nonce", "a")), "b").andExpect(status().isUnauthorized());
        login("google", token(c -> {}), "expected-nonce").andExpect(status().isUnauthorized()); // nonce 없는 토큰
        login("google", "not-a-jwt", null).andExpect(status().isUnauthorized());
        assertThat(accounts()).isZero();
    }

    @Test void existingEmailAccountIsNotLinked() throws Exception {
        jdbc.update("INSERT INTO users(email,name,nickname,password_hash) VALUES (?,'기존','기존" + sub.substring(2, 12) + "','x')", sub + "@gmail.test");
        login("google", token(c -> {}), null).andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(4007))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("이메일 로그인")));
        assertThat(accounts()).isZero();
    }

    @Test void unverifiedOrMissingEmailUsesPlaceholder() throws Exception {
        login("google", token(c -> c.claim("email_verified", false)), null).andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT u.email FROM users u JOIN oauth_accounts a ON a.user_id=u.id WHERE a.provider_id=?", String.class, sub))
                .isEqualTo("google_" + sub + "@oauth.local");
    }

    @Test void withdrawnUserCanSignUpAgain() throws Exception {
        String access = json.readTree(login("google", token(c -> {}), null).andReturn().getResponse().getContentAsString())
                .at("/data/accessToken").asText();
        mvc.perform(delete("/api/auth/withdraw").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        em.flush();
        login("google", token(c -> {}), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.newUser").value(true));
        em.flush();
        assertThat(accounts()).isEqualTo(1);
        // 탈퇴 계정이 이메일을 점유하므로 새 계정은 대체 이메일
        assertThat(jdbc.queryForObject("SELECT u.email FROM users u JOIN oauth_accounts a ON a.user_id=u.id WHERE a.provider_id=?", String.class, sub))
                .isEqualTo("google_" + sub + "@oauth.local");
    }

    /** 회귀: 탈퇴 사용자의 닉네임·이메일은 UNIQUE에 남아 있으므로 중복으로 판단해야 한다(이전에는 500). */
    @Test void withdrawnUsersNicknameAndEmailStayReserved() throws Exception {
        String nick = "탈퇴" + sub.substring(2, 12);
        jdbc.update("INSERT INTO users(email,name,nickname,deleted_at) VALUES (?,'t',?,now())", sub + "@old.test", nick);
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(json.writeValueAsString(Map.of(
                "email", sub + "@new.test", "password", "Passw0rd!" + sub.substring(2, 8), "name", "n", "nickname", nick))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(4102));
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(json.writeValueAsString(Map.of(
                "email", sub + "@old.test", "password", "Passw0rd!" + sub.substring(2, 8), "name", "n", "nickname", nick + "2"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(4101));
    }

    @Test void unsupportedUnconfiguredAndInvalidRequests() throws Exception {
        login("apple", "x", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(4005));
        login("kakao", "x", null).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(5004));
        mvc.perform(post("/api/auth/oauth/google").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
    }
}
