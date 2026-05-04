package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.ArticleTermHighlight;
import com.youthletter.domain.news.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArticleTermHighlightRepository extends JpaRepository<ArticleTermHighlight, Long> {

    @Query(value = """
            SELECT DISTINCT na.* FROM news_articles na
            JOIN article_term_highlights ath ON ath.article_id = na.id
            WHERE ath.term_id = :termId
            ORDER BY na.published_at DESC NULLS LAST
            LIMIT 5
            """, nativeQuery = true)
    List<NewsArticle> findTop5ArticlesByTermId(@Param("termId") Long termId);
}
