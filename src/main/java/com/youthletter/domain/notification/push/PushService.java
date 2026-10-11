package com.youthletter.domain.notification.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 단위 푸시 발송.
 * - push.enabled=false(기본)이면 아무것도 보내지 않는다.
 * - 알림 설정(dailyDigest/quizReminder/streakAlert)이 꺼진 종류는 보내지 않는다(저장 전 기본값은 켜짐).
 * - 같은 사용자·종류·대상·한국 날짜는 1회만(push_logs 유니크). 발송 전에 기록을 선점하므로 최대 1회 전송.
 * - Expo가 DeviceNotRegistered로 답한 토큰은 삭제한다.
 * - 외부 HTTP 호출 동안 DB 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {
    private static final String TODAY_KST = "(statement_timestamp() AT TIME ZONE 'Asia/Seoul')::date";
    private final JdbcTemplate jdbc;
    private final ExpoPushClient expo;

    @Value("${push.enabled:false}")
    private boolean enabled;

    public record Result(boolean attempted, int devices, int succeeded) {
        static final Result SKIPPED = new Result(false, 0, 0);
    }

    public boolean isEnabled() { return enabled; }

    public Result send(Long userId, PushType type, String refKey, String title, String body, Map<String, Object> data) {
        if (!enabled || !allowedBySetting(userId, type)) return Result.SKIPPED;
        String key = type == PushType.TEST ? UUID.randomUUID().toString() : (refKey == null ? "" : refKey);
        List<Long> claimed = jdbc.queryForList("INSERT INTO push_logs(user_id, type, ref_key, sent_on) VALUES (?, ?, ?, " + TODAY_KST
                + ") ON CONFLICT DO NOTHING RETURNING id", Long.class, userId, type.name(), key);
        if (claimed.isEmpty()) return Result.SKIPPED; // 오늘 이미 보냄
        List<String> tokens = jdbc.queryForList("SELECT token FROM device_tokens WHERE user_id=?", String.class, userId)
                .stream().filter(ExpoPushClient::isExpoToken).toList();
        int succeeded = 0;
        if (!tokens.isEmpty()) {
            Map<String, Object> payload = new java.util.HashMap<>(data == null ? Map.of() : data);
            payload.put("type", type.name());
            List<ExpoPushClient.Ticket> tickets = expo.send(tokens.stream()
                    .map(t -> new ExpoPushClient.Message(t, title, body, payload)).toList());
            for (int i = 0; i < tickets.size(); i++) {
                if (tickets.get(i).ok()) succeeded++;
                else if (tickets.get(i).deviceGone())
                    jdbc.update("DELETE FROM device_tokens WHERE user_id=? AND token=?", userId, tokens.get(i));
            }
        }
        jdbc.update("UPDATE push_logs SET devices=?, succeeded=? WHERE id=?", tokens.size(), succeeded, claimed.get(0));
        return new Result(true, tokens.size(), succeeded);
    }

    private boolean allowedBySetting(Long userId, PushType type) {
        String column = switch (type) {
            case DAILY_DIGEST -> "daily_digest";
            case QUIZ_REMINDER -> "quiz_reminder";
            case STREAK_ALERT -> "streak_alert";
            default -> null;
        };
        if (column == null) return true;
        Boolean on = jdbc.query("SELECT " + column + " FROM notification_settings WHERE user_id=?",
                (rs, i) -> rs.getBoolean(1), userId).stream().findFirst().orElse(true);
        return on;
    }
}
