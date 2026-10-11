package com.youthletter.domain.quiz.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 꿀 지급 규칙(확정): 퀴즈 문제당 첫 정답 10꿀, 기사 읽음 기사당 하루(한국 날짜) 1회 5꿀.
 * 중복 지급은 honey_ledger 부분 유니크 인덱스 + ON CONFLICT DO NOTHING으로 막는다(재시도·동시 요청 안전).
 */
@Service
@RequiredArgsConstructor
public class HoneyService {
    public static final int QUIZ_CORRECT = 10;
    public static final int ARTICLE_READ = 5;
    private static final String TODAY_KST = "(statement_timestamp() AT TIME ZONE 'Asia/Seoul')::date";
    private final JdbcTemplate jdbc;

    /** @return 이번 요청으로 지급된 꿀(이미 받았으면 0) */
    @Transactional
    public int awardRead(Long userId, Long articleId) {
        return jdbc.update("INSERT INTO honey_ledger(user_id, reason, article_id, amount, earned_on) VALUES (?, 'ARTICLE_READ', ?, ?, "
                + TODAY_KST + ") ON CONFLICT DO NOTHING", userId, articleId, ARTICLE_READ) * ARTICLE_READ;
    }

    @Transactional
    public int awardQuiz(Long userId, Long quizId, Long articleId) {
        return jdbc.update("INSERT INTO honey_ledger(user_id, reason, article_id, quiz_id, amount, earned_on) VALUES (?, 'QUIZ_CORRECT', ?, ?, ?, "
                + TODAY_KST + ") ON CONFLICT DO NOTHING", userId, articleId, quizId, QUIZ_CORRECT) * QUIZ_CORRECT;
    }

    @Transactional(readOnly = true)
    public long total(Long userId) {
        Long sum = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM honey_ledger WHERE user_id=?", Long.class, userId);
        return sum == null ? 0 : sum;
    }
}
