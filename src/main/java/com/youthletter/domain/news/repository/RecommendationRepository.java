package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface RecommendationRepository extends JpaRepository<NewsArticle, Long> {
    interface Candidate {
        Long getId(); String getTitle(); String getCategory(); Double getScore(); String getReasonCode();
    }
    @Query(value = """
        WITH actions AS (
            SELECT DISTINCT article_id FROM reading_history
            WHERE user_id = :userId AND read_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
        ), vectors AS (
            SELECT a.embedding FROM news_articles a JOIN actions r ON r.article_id = a.id
            WHERE a.embedding IS NOT NULL AND (a.embedding <#> a.embedding) < 0
            UNION ALL
            SELECT a.embedding FROM news_articles a JOIN news_bookmarks b ON b.article_id = a.id
            CROSS JOIN generate_series(1, 3)
            WHERE b.user_id = :userId AND a.embedding IS NOT NULL AND (a.embedding <#> a.embedding) < 0
        ), profile AS (SELECT avg(embedding) AS embedding FROM vectors), scored AS (
            SELECT a.id, a.title, a.category,
                CASE WHEN p.embedding IS NOT NULL AND (p.embedding <#> p.embedding) < 0
                       AND a.embedding IS NOT NULL AND (a.embedding <#> a.embedding) < 0
                    THEN GREATEST(0, 1 - (a.embedding <=> p.embedding)) ELSE 0 END AS similarity,
                CASE WHEN COALESCE(u.interests, '[]'::jsonb) @> jsonb_build_array(a.category)
                    THEN 1 ELSE 0 END AS interest,
                1.0 / (1.0 + GREATEST(0, EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - COALESCE(a.published_at, a.crawled_at))) / 86400)) AS freshness
            FROM news_articles a CROSS JOIN profile p JOIN users u ON u.id = :userId
            WHERE NOT EXISTS (SELECT 1 FROM reading_history r WHERE r.user_id = :userId AND r.article_id = a.id)
        )
        SELECT id, title, category, (0.6 * similarity + 0.25 * interest + 0.15 * freshness) AS score,
            CASE WHEN similarity > 0 THEN 'SIMILAR_TO_ACTIVITY'
                 WHEN interest = 1 THEN 'INTEREST_CATEGORY' ELSE 'LATEST_NEWS' END AS "reasonCode"
        FROM scored ORDER BY score DESC, id DESC LIMIT 30
        """, nativeQuery = true)
    List<Candidate> recommend(@Param("userId") Long userId);
}
