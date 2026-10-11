package com.youthletter.domain.quiz.service;

import com.youthletter.domain.quiz.dto.ActivityDayResponse;
import com.youthletter.domain.quiz.dto.InsightResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** 히트맵(A19)·관심/약점 분야(A22). 날짜 경계는 한국 시간. */
@Service
@RequiredArgsConstructor
public class LearningService {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final int DEFAULT_DAYS = 126, MAX_DAYS = 365;
    private static final int INTEREST_WINDOW_DAYS = 30, INTEREST_LIMIT = 3;
    private static final int WEAK_MIN_QUIZZES = 3, WEAK_LIMIT = 2;
    private static final double WEAK_THRESHOLD = 0.6;
    private final JdbcTemplate jdbc;

    /** 오늘 포함 최근 days일, 활동 없는 날도 0으로 채워 오래된 날짜부터 반환한다. */
    @Transactional(readOnly = true)
    public List<ActivityDayResponse> activity(Long userId, int days) {
        int span = Math.max(1, Math.min(MAX_DAYS, days));
        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(span - 1L);
        Map<LocalDate, Integer> honey = new HashMap<>();
        jdbc.query("SELECT earned_on, sum(amount) AS honey FROM honey_ledger WHERE user_id=? AND earned_on BETWEEN ? AND ? GROUP BY earned_on",
                rs -> { honey.put(rs.getObject("earned_on", LocalDate.class), rs.getInt("honey")); }, userId, from, today);
        List<ActivityDayResponse> out = new ArrayList<>(span);
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            int h = honey.getOrDefault(d, 0);
            out.add(new ActivityDayResponse(d, ActivityDayResponse.level(h), h));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public InsightResponse insights(Long userId) {
        // 문제별 첫 시도만 정답률에 반영한다(재도전으로 정답률이 부풀지 않도록).
        String firstAttempts = """
                SELECT DISTINCT ON (a.quiz_id) a.quiz_id, a.is_correct, n.category
                FROM quiz_attempts a JOIN quizzes q ON q.id = a.quiz_id JOIN news_articles n ON n.id = q.article_id
                WHERE a.user_id = ? ORDER BY a.quiz_id, a.attempted_at, a.id
                """;
        Map<String, Object> overall = jdbc.queryForMap("SELECT count(*) AS total, count(*) FILTER (WHERE is_correct) AS correct FROM ("
                + firstAttempts + ") f", userId);
        int total = ((Number) overall.get("total")).intValue();
        Integer rate = total == 0 ? null : (int) Math.round(100.0 * ((Number) overall.get("correct")).intValue() / total);

        List<String> interested = jdbc.queryForList("""
                SELECT n.category FROM reading_history r JOIN news_articles n ON n.id = r.article_id
                WHERE r.user_id = ? AND r.read_at >= now() - make_interval(days => ?)
                GROUP BY n.category ORDER BY count(DISTINCT n.id) DESC, max(r.read_at) DESC LIMIT ?
                """, String.class, userId, INTEREST_WINDOW_DAYS, INTEREST_LIMIT);
        List<String> weak = jdbc.queryForList("SELECT category FROM (" + firstAttempts + ") f GROUP BY category "
                + "HAVING count(*) >= ? AND avg(CASE WHEN is_correct THEN 1.0 ELSE 0 END) < ? "
                + "ORDER BY avg(CASE WHEN is_correct THEN 1.0 ELSE 0 END), count(*) DESC LIMIT ?",
                String.class, userId, WEAK_MIN_QUIZZES, WEAK_THRESHOLD, WEAK_LIMIT);
        return new InsightResponse(rate, total, interested, weak);
    }

    /** 오늘(한국 날짜) 기사를 1개 이상 읽었는지 = 오늘 학습 완료 */
    @Transactional(readOnly = true)
    public boolean completedToday(Long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM reading_history WHERE user_id = ?
                  AND (read_at AT TIME ZONE 'Asia/Seoul')::date = (statement_timestamp() AT TIME ZONE 'Asia/Seoul')::date)
                """, Boolean.class, userId));
    }

    @Transactional(readOnly = true)
    public long quizCount(Long userId) {
        Long n = jdbc.queryForObject("SELECT count(DISTINCT quiz_id) FROM quiz_attempts WHERE user_id=?", Long.class, userId);
        return n == null ? 0 : n;
    }
}
