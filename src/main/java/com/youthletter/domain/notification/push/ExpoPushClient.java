package com.youthletter.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Expo Push API(https://exp.host/--/api/v2/push/send) 발송기.
 * 앱(Expo)이 expo-notifications로 받은 ExponentPushToken만 보낸다. 한 요청에 최대 100건.
 * 응답 ticket의 DeviceNotRegistered는 호출자가 토큰을 삭제하도록 알려준다.
 */
@Slf4j
@Component
public class ExpoPushClient {
    static final int BATCH = 100;
    private final RestClient client;
    private final String accessToken;

    public record Message(String to, String title, String body, Map<String, Object> data) {}
    /** status: ok | error, error: DeviceNotRegistered 등 */
    public record Ticket(String status, String error) {
        public boolean ok() { return "ok".equals(status); }
        public boolean deviceGone() { return "DeviceNotRegistered".equals(error); }
    }

    public ExpoPushClient(@Value("${push.expo-url:https://exp.host/--/api/v2/push/send}") String url,
                          @Value("${push.expo-access-token:}") String accessToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.accessToken = accessToken;
    }

    public static boolean isExpoToken(String token) {
        return token != null && token.matches("^(ExponentPushToken|ExpoPushToken)\\[[^\\]]+]$");
    }

    /** 메시지 순서대로 ticket을 돌려준다. 통신 실패 시 해당 묶음은 모두 error(TransportError). */
    public List<Ticket> send(List<Message> messages) {
        List<Ticket> out = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i += BATCH) {
            List<Message> batch = messages.subList(i, Math.min(i + BATCH, messages.size()));
            List<Map<String, Object>> body = batch.stream().map(m -> Map.<String, Object>of(
                    "to", m.to(), "title", m.title(), "body", m.body(), "sound", "default",
                    "data", m.data() == null ? Map.of() : m.data())).toList();
            try {
                var request = client.post().contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).body(body);
                if (StringUtils.hasText(accessToken)) request = request.header("Authorization", "Bearer " + accessToken);
                Map<?, ?> response = request.retrieve().body(Map.class);
                List<?> data = response == null ? null : (List<?>) response.get("data");
                for (int j = 0; j < batch.size(); j++) {
                    Map<?, ?> t = data != null && j < data.size() ? (Map<?, ?>) data.get(j) : null;
                    Map<?, ?> details = t == null ? null : (Map<?, ?>) t.get("details");
                    out.add(t == null ? new Ticket("error", "MissingTicket")
                            : new Ticket(String.valueOf(t.get("status")), details == null ? null : (String) details.get("error")));
                }
            } catch (RuntimeException e) {
                // 토큰·본문은 로그에 남기지 않는다.
                log.warn("[Push] Expo 발송 실패: batch={}, reason={}", batch.size(), e.getClass().getSimpleName());
                batch.forEach(m -> out.add(new Ticket("error", "TransportError")));
            }
        }
        return out;
    }
}
