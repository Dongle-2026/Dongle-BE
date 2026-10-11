package com.youthletter.global.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${oauth2.redirect-uri:http://localhost:3000/auth/callback}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.error("OAuth2 로그인 실패: {}", exception.getMessage());

        // 같은 이메일로 이미 가입된 경우: error=email_already_registered, message=기존 가입 방법 안내
        String code = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode() : "oauth_failed";
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("error", code)
                .queryParam("message", exception.getMessage())
                .build().encode().toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}