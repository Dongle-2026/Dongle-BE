package com.youthletter.domain.news.service;

import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContentGenerationStatusService {
    private final JdbcTemplate jdbc;
    public record Stage(String kind, String status) {}

    @Transactional(readOnly = true)
    public List<Stage> get(Long articleId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM news_articles WHERE id=?)", Boolean.class, articleId)))
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        // 공개 상태에는 내부 오류, 작업 식별자, 사용자 정보, 원문을 포함하지 않는다.
        return jdbc.query("""
            SELECT kinds.kind, COALESCE(latest.status, 'NOT_REQUESTED') AS status
            FROM (VALUES ('ARTICLE', 1), ('CARD', 2), ('QUIZ', 3)) AS kinds(kind, position)
            LEFT JOIN LATERAL (
                SELECT status FROM ai_jobs WHERE article_id=? AND kind=kinds.kind ORDER BY id DESC LIMIT 1
            ) latest ON true ORDER BY kinds.position
            """, (rs, row) -> new Stage(rs.getString("kind"), rs.getString("status")), articleId);
    }
}
