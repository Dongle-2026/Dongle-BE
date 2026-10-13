package com.youthletter.domain.notification.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 정기 푸시(한국 시간). 대상은 기기 토큰이 있고 해당 알림 설정이 켜진 사용자.
 * - 08:00 오늘의 뉴스: 오늘 들어온 기사가 있을 때
 * - 19:00 퀴즈 리마인더: 오늘 읽은 기사에 아직 맞히지 않은 퀴즈가 있을 때
 * - 21:00 스트릭 위기: 어제는 학습했지만 오늘은 아직 기사를 읽지 않았을 때
 * 크론을 "-"로 설정하면 해당 알림을 끈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushScheduler {
    private static final String KST_DAY = "(statement_timestamp() AT TIME ZONE 'Asia/Seoul')::date";
    private static final String HAS_DEVICE = "EXISTS(SELECT 1 FROM device_tokens d WHERE d.user_id = u.id)";
    private final JdbcTemplate jdbc;
    private final PushService push;

    @Scheduled(cron = "${push.daily-digest-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void dailyDigest() {
        if (!push.isEnabled()) return;
        Map<String, Object> today = jdbc.queryForMap("SELECT count(*) AS n, (array_agg(title ORDER BY coalesce(published_at, crawled_at) DESC))[1] AS top "
                + "FROM news_articles WHERE (coalesce(published_at, crawled_at) AT TIME ZONE 'Asia/Seoul')::date = " + KST_DAY);
        long count = ((Number) today.get("n")).longValue();
        if (count == 0) return;
        String body = "오늘의 경제 뉴스 " + count + "건이 도착했어요. " + today.get("top");
        int sent = 0;
        for (Long userId : targets("daily_digest", "")) {
            if (push.send(userId, PushType.DAILY_DIGEST, "", "오늘의 뉴스", truncate(body), Map.of()).succeeded() > 0) sent++;
        }
        log.info("[Push] 오늘의 뉴스 발송 사용자 {}명", sent);
    }

    @Scheduled(cron = "${push.quiz-reminder-cron:0 0 19 * * *}", zone = "Asia/Seoul")
    public void quizReminder() {
        if (!push.isEnabled()) return;
        String condition = """
                AND EXISTS (SELECT 1 FROM reading_history r JOIN quizzes q ON q.article_id = r.article_id
                    WHERE r.user_id = u.id AND (r.read_at AT TIME ZONE 'Asia/Seoul')::date = %s
                      AND NOT EXISTS (SELECT 1 FROM quiz_attempts a WHERE a.user_id = u.id AND a.quiz_id = q.id AND a.is_correct))
                """.formatted(KST_DAY);
        int sent = 0;
        for (Long userId : targets("quiz_reminder", condition)) {
            if (push.send(userId, PushType.QUIZ_REMINDER, "", "퀴즈 풀고 꿀 받기", "오늘 읽은 기사의 퀴즈가 남아 있어요. 맞히면 10꿀!", Map.of()).succeeded() > 0) sent++;
        }
        log.info("[Push] 퀴즈 리마인더 발송 사용자 {}명", sent);
    }

    @Scheduled(cron = "${push.streak-alert-cron:0 0 21 * * *}", zone = "Asia/Seoul")
    public void streakAlert() {
        if (!push.isEnabled()) return;
        String condition = """
                AND EXISTS (SELECT 1 FROM reading_history r WHERE r.user_id = u.id
                    AND (r.read_at AT TIME ZONE 'Asia/Seoul')::date = %1$s - 1)
                AND NOT EXISTS (SELECT 1 FROM reading_history r WHERE r.user_id = u.id
                    AND (r.read_at AT TIME ZONE 'Asia/Seoul')::date = %1$s)
                """.formatted(KST_DAY);
        int sent = 0;
        for (Long userId : targets("streak_alert", condition)) {
            if (push.send(userId, PushType.STREAK_ALERT, "", "연속 학습이 끊기기 직전이에요", "오늘 기사 1개만 읽으면 연속 학습이 이어져요.", Map.of()).succeeded() > 0) sent++;
        }
        log.info("[Push] 스트릭 위기 발송 사용자 {}명", sent);
    }

    /** 기기가 있고 설정이 켜진(행이 없으면 기본 켜짐) 활성 사용자 */
    private List<Long> targets(String settingColumn, String extraCondition) {
        return jdbc.queryForList("SELECT u.id FROM users u LEFT JOIN notification_settings s ON s.user_id = u.id "
                + "WHERE u.deleted_at IS NULL AND coalesce(s." + settingColumn + ", true) AND " + HAS_DEVICE + " " + extraCondition
                + " ORDER BY u.id", Long.class);
    }

    private static String truncate(String text) {
        return text.length() <= 120 ? text : text.substring(0, 119) + "…";
    }
}
