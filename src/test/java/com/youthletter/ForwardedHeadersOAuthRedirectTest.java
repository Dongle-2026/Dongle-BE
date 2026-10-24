package com.youthletter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTPS 프록시(Caddy·터널) 뒤에서 카카오에 보내는 콜백 주소가 https://도메인이 되는지 실제 Tomcat으로 확인한다.
 * 프록시 헤더가 무시되면 콜백이 http://로 만들어져 카카오 콘솔 등록 주소와 달라지고 로그인이 실패한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"crawler.cron=-", "policy.notification-cron=-", "ai-jobs.enabled=false"})
@ActiveProfiles("test")
class ForwardedHeadersOAuthRedirectTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private String kakaoCallback(String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/oauth2/authorization/kakao"))
                .timeout(Duration.ofSeconds(10));
        if (headers.length > 0) request.headers(headers);
        var response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElseThrow();
        String query = URI.create(location).getRawQuery();
        for (String pair : query.split("&")) {
            if (pair.startsWith("redirect_uri=")) return URLDecoder.decode(pair.substring(13), StandardCharsets.UTF_8);
        }
        throw new AssertionError("redirect_uri 없음: " + location);
    }

    @Test void behindHttpsProxyTheCallbackUsesTheProxyHostAndHttps() throws Exception {
        assertThat(kakaoCallback("X-Forwarded-Proto", "https", "X-Forwarded-Host", "api.example.com"))
                .isEqualTo("https://api.example.com/login/oauth2/code/kakao");
    }

    @Test void withoutProxyHeadersTheCallbackStaysOnTheDirectAddress() throws Exception {
        assertThat(kakaoCallback()).isEqualTo("http://127.0.0.1:" + port + "/login/oauth2/code/kakao");
    }
}
