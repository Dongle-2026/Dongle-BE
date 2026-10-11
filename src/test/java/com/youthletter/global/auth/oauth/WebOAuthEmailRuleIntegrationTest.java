package com.youthletter.global.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 웹 소셜 로그인도 같은 이메일 기존 계정은 거부하고 기존 가입 방법을 안내한다(모바일과 같은 규칙). */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @Transactional
class WebOAuthEmailRuleIntegrationTest {
    @Autowired CustomOAuth2UserService service;
    @Autowired OAuth2AuthenticationFailureHandler failureHandler;
    @Autowired JdbcTemplate jdbc;
    String m;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        m = UUID.randomUUID().toString().substring(0, 8);
    }
    static KakaoUserInfo kakao(String id, String email, boolean verified) {
        return new KakaoUserInfo(Map.of("id", id, "properties", Map.of("nickname", "카카오"),
                "kakao_account", Map.of("email", email, "is_email_verified", verified, "is_email_valid", true)));
    }
    static GoogleUserInfo google(String sub, String email, boolean verified) {
        return new GoogleUserInfo(Map.of("sub", sub, "email", email, "email_verified", verified, "name", "구글"));
    }
    Long localUser(String email) {
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname,password_hash) VALUES (?,'t',?,'x') RETURNING id", Long.class, email, "w" + m);
    }

    @Test void sameEmailAsEmailAccountIsRejectedWithEmailLoginGuidance() {
        localUser(m + "@test.invalid");
        assertThatThrownBy(() -> service.processOAuthUser("KAKAO", kakao("k" + m, m + "@test.invalid", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    var ex = (OAuth2AuthenticationException) e;
                    assertThat(ex.getError().getErrorCode()).isEqualTo("email_already_registered");
                    assertThat(ex.getMessage()).contains("이메일 로그인");
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth_accounts WHERE provider_id=?", Integer.class, "k" + m)).isZero();
    }

    @Test void sameEmailAsOtherProviderGuidesToThatProvider() {
        var first = service.processOAuthUser("KAKAO", kakao("k" + m, m + "@test.invalid", true));
        assertThatThrownBy(() -> service.processOAuthUser("GOOGLE", google("g" + m, m + "@test.invalid", true)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("카카오 로그인");
        // 같은 제공자 계정은 그대로 로그인
        assertThat(service.processOAuthUser("KAKAO", kakao("k" + m, m + "@test.invalid", true)).getId()).isEqualTo(first.getId());
    }

    @Test void unverifiedEmailIsNotComparedOrStored() {
        localUser(m + "@test.invalid");
        var user = service.processOAuthUser("GOOGLE", google("g" + m, m + "@test.invalid", false));
        assertThat(user.getEmail()).isEqualTo("google_g" + m + "@oauth.local");
    }

    @Test void failureRedirectCarriesCodeAndGuidance() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        failureHandler.onAuthenticationFailure(request, response, new OAuth2AuthenticationException(
                new org.springframework.security.oauth2.core.OAuth2Error("email_already_registered"), "이미 카카오 로그인으로 가입된 이메일입니다."));
        var params = UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build().getQueryParams();
        assertThat(params.getFirst("error")).isEqualTo("email_already_registered");
        assertThat(java.net.URLDecoder.decode(params.getFirst("message"), java.nio.charset.StandardCharsets.UTF_8)).contains("카카오 로그인");
        assertThat(response.getRedirectedUrl()).doesNotContain("accessToken");
    }
}
