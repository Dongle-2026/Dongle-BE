package com.youthletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 TCP 서버·회원가입 토큰·Python 진단 도구를 연결한다. MockMvc와 별도 검증이다. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"crawler.cron=-","policy.notification-cron=-","ai-jobs.enabled=false"})
@ActiveProfiles("test")
class ApiHttpSmokeIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test void allReportedRoutesPassOverHttpWithNewAccount() throws Exception {
        String marker=UUID.randomUUID().toString(),email=marker+"@smoke.invalid";
        String base="http://127.0.0.1:"+port;
        Long article=null;
        Process probe=null;
        try {
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var request=HttpRequest.newBuilder(URI.create(base+"/api/auth/signup")).timeout(Duration.ofSeconds(10))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                            Map.of("email",email,"password","Fixture123!","name","HTTP 시험","nickname",marker.substring(0,20))))).build();
            var signed=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(signed.statusCode()).isEqualTo(201);
            String token=json.readTree(signed.body()).at("/data/accessToken").asText();
            assertThat(token).isNotBlank();
            article=jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'http-test','HTTP 시험','시험 본문입니다.','금융',now()) RETURNING id",
                    Long.class,"https://example.invalid/http-"+marker);
            var builder=new ProcessBuilder("python3",Path.of("scripts/api_smoke.py").toAbsolutePath().toString(),"--base-url",base,"--article-id",article.toString());
            builder.environment().put("API_ACCESS_TOKEN",token);
            probe=builder.redirectErrorStream(true).start();
            assertThat(probe.waitFor(45,TimeUnit.SECONDS)).isTrue();
            String report=new String(probe.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(report).doesNotContain(token,email);
            assertThat(probe.exitValue()).as("HTTP API diagnostic results: %s",report).isZero();
            assertThat(json.readTree(report).size()).isEqualTo(8);
        } finally {
            if(probe!=null && probe.isAlive()) { probe.destroyForcibly();probe.waitFor(5,TimeUnit.SECONDS); }
            // 이 시험이 만든 사용자/기사만 지운다. 기존 자료는 보존한다.
            if(article!=null) jdbc.update("DELETE FROM news_articles WHERE id=?",article);
            var ids=jdbc.queryForList("SELECT id FROM users WHERE email=?",Long.class,email);
            if(!ids.isEmpty()) {
                var redisKey="refresh:"+ids.get(0);
                // 회원가입이 발급한 refresh 키도 시험 종료 때 정리한다.
                cleanupRefresh(redisKey);
                jdbc.update("DELETE FROM users WHERE id=?",ids.get(0));
            }
        }
    }
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    void cleanupRefresh(String key) { redis.delete(key); }
}
