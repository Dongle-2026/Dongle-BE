package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.youthletter.domain.notification.push.PushScheduler;
import com.youthletter.domain.notification.push.PushService;
import com.youthletter.domain.notification.push.PushType;
import com.youthletter.domain.policy.service.PolicyScheduler;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
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
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Expo 푸시 발송: 설정 반영, 하루 1회, 잘못된 토큰 정리, 정기 알림 대상 선정, 정책 마감 발송 표시. 로컬 가짜 Expo 서버 사용. */
@SpringBootTest(properties = {"crawler.cron=-", "push.enabled=true", "push.daily-digest-cron=-",
        "push.quiz-reminder-cron=-", "push.streak-alert-cron=-"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class PushNotificationIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<JsonNode> RECEIVED = new CopyOnWriteArrayList<>();
    static final HttpServer EXPO = start();

    static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/push/send", ex -> {
                JsonNode messages = JSON.readTree(ex.getRequestBody());
                StringBuilder data = new StringBuilder("{\"data\":[");
                for (int i = 0; i < messages.size(); i++) {
                    RECEIVED.add(messages.get(i));
                    boolean gone = messages.get(i).get("to").asText().contains("gone");
                    if (i > 0) data.append(',');
                    data.append(gone ? "{\"status\":\"error\",\"details\":{\"error\":\"DeviceNotRegistered\"}}" : "{\"status\":\"ok\",\"id\":\"t\"}");
                }
                byte[] body = data.append("]}").toString().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
                ex.close();
            });
            server.start();
            return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @AfterAll static void stop() { EXPO.stop(0); }
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("push.expo-url", () -> "http://127.0.0.1:" + EXPO.getAddress().getPort() + "/push/send");
    }

    @Autowired PushService push;
    @Autowired PushScheduler scheduler;
    @Autowired PolicyScheduler policyScheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    String m;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        RECEIVED.clear();
        m = UUID.randomUUID().toString().substring(0, 8);
        // 다른 테스트 데이터의 기기 토큰이 정기 알림 대상에 섞이지 않도록 이 트랜잭션 안에서만 비운다
        jdbc.update("DELETE FROM device_tokens");
    }
    Long user(String key) {
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id", Long.class, key + m + "@test.invalid", key + m);
    }
    void device(Long userId, String id, String token) {
        jdbc.update("INSERT INTO device_tokens(user_id,device_id,token,platform) VALUES (?,?,?,'IOS')", userId, id, token);
    }
    List<String> receivedTo() { return RECEIVED.stream().map(n -> n.get("to").asText()).toList(); }

    @Test void sendsToExpoTokensOnceADayAndRemovesDeadTokens() {
        Long u = user("a");
        device(u, "phone", "ExponentPushToken[ok-" + m + "]");
        device(u, "old", "ExponentPushToken[gone-" + m + "]");
        device(u, "fcm", "fcm-raw-token-" + m); // Expo 형식 아님 → 보내지 않음
        var first = push.send(u, PushType.DAILY_DIGEST, "", "제목", "본문", Map.of("articleId", 1));
        assertThat(first.devices()).isEqualTo(2);
        assertThat(first.succeeded()).isEqualTo(1);
        assertThat(receivedTo()).containsExactlyInAnyOrder("ExponentPushToken[ok-" + m + "]", "ExponentPushToken[gone-" + m + "]");
        assertThat(RECEIVED.get(0).at("/data/type").asText()).isEqualTo("DAILY_DIGEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE user_id=?", Integer.class, u)).isEqualTo(2); // gone 삭제
        assertThat(push.send(u, PushType.DAILY_DIGEST, "", "제목", "본문", Map.of()).attempted()).isFalse(); // 오늘 이미 보냄
        assertThat(RECEIVED).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT succeeded FROM push_logs WHERE user_id=? AND type='DAILY_DIGEST'", Integer.class, u)).isEqualTo(1);
    }

    @Test void respectsNotificationSettings() {
        Long u = user("b");
        device(u, "phone", "ExponentPushToken[b-" + m + "]");
        jdbc.update("INSERT INTO notification_settings(user_id, daily_digest, quiz_reminder, streak_alert) VALUES (?, false, true, true)", u);
        assertThat(push.send(u, PushType.DAILY_DIGEST, "", "t", "b", Map.of()).attempted()).isFalse();
        assertThat(push.send(u, PushType.QUIZ_REMINDER, "", "t", "b", Map.of()).succeeded()).isEqualTo(1);
        assertThat(push.send(u, PushType.POLICY_DEADLINE, "p:1", "t", "b", Map.of()).succeeded()).isEqualTo(1); // 설정 항목 없음 → 발송
    }

    @Test void scheduledJobsPickTheRightUsers() {
        Long reader = user("r"), streak = user("s"), off = user("x"), noDevice = user("n");
        device(reader, "p", "ExponentPushToken[r-" + m + "]");
        device(streak, "p", "ExponentPushToken[s-" + m + "]");
        device(off, "p", "ExponentPushToken[x-" + m + "]");
        jdbc.update("INSERT INTO notification_settings(user_id, daily_digest, quiz_reminder, streak_alert) VALUES (?, false, false, false)", off);
        Long article = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'t','오늘의 기사','본문','금융',now()) RETURNING id",
                Long.class, "push-" + m);
        jdbc.update("INSERT INTO quizzes(article_id,quiz_order,question,choices,answer_index,explanation,source_sentence_ids) VALUES (?,1,'q','[\"a\",\"b\",\"c\",\"d\"]'::jsonb,0,'e','[1]'::jsonb)", article);
        for (Long u : List.of(reader, off, noDevice)) jdbc.update("INSERT INTO reading_history(user_id,article_id,read_at) VALUES (?,?,now())", u, article);
        jdbc.update("INSERT INTO reading_history(user_id,article_id,read_at) VALUES (?,?,now() - interval '1 day')", streak, article);

        scheduler.dailyDigest();
        assertThat(receivedTo()).containsExactlyInAnyOrder("ExponentPushToken[r-" + m + "]", "ExponentPushToken[s-" + m + "]");
        assertThat(RECEIVED.get(0).get("body").asText()).contains("오늘의 기사");
        RECEIVED.clear();
        scheduler.quizReminder(); // 오늘 읽었고 안 푼 퀴즈 있음
        assertThat(receivedTo()).containsExactly("ExponentPushToken[r-" + m + "]");
        RECEIVED.clear();
        scheduler.streakAlert(); // 어제 학습, 오늘 미학습
        assertThat(receivedTo()).containsExactly("ExponentPushToken[s-" + m + "]");
        RECEIVED.clear();
        scheduler.dailyDigest(); // 하루 1회
        assertThat(RECEIVED).isEmpty();
    }

    @Test void policyDeadlineIsMarkedSentOnlyWhenDelivered() {
        Long withDevice = user("p"), without = user("q");
        device(withDevice, "p", "ExponentPushToken[p-" + m + "]");
        Long policy = jdbc.queryForObject("INSERT INTO policies(title, description, organization, end_date) VALUES (?, 'd', 'o', (now() AT TIME ZONE 'UTC')::date + 3) RETURNING id", Long.class, "정책" + m);
        for (Long u : List.of(withDevice, without)) jdbc.update("INSERT INTO policy_bookmarks(user_id, policy_id) VALUES (?, ?)", u, policy);
        policyScheduler.sendDeadlineNotifications();
        assertThat(jdbc.queryForObject("SELECT is_sent FROM policy_notifications WHERE user_id=? AND policy_id=?", Boolean.class, withDevice, policy)).isTrue();
        assertThat(jdbc.queryForObject("SELECT is_sent FROM policy_notifications WHERE user_id=? AND policy_id=?", Boolean.class, without, policy)).isFalse();
        assertThat(RECEIVED.get(0).get("body").asText()).contains("정책" + m);
        assertThat(RECEIVED.get(0).at("/data/policyId").asLong()).isEqualTo(policy);
    }

    @Test void testPushEndpoint() throws Exception {
        Long u = user("t");
        device(u, "p", "ExponentPushToken[t-" + m + "]");
        String bearer = "Bearer " + tokens.createAccessToken(u, "t" + m + "@test.invalid", "USER");
        for (int i = 0; i < 2; i++)
            mvc.perform(post("/api/me/push/test").header("Authorization", bearer)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.devices").value(1)).andExpect(jsonPath("$.data.succeeded").value(1));
        assertThat(RECEIVED).hasSize(2); // 테스트 알림은 하루 제한 없음
        mvc.perform(post("/api/me/push/test")).andExpect(status().isUnauthorized());
    }
}
