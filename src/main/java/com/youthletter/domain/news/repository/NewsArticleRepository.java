package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    /**
     * 카테고리별 뉴스 목록 (홈 화면)
     * Pageable로 published_at DESC 정렬 + 페이지네이션
     */
    Page<NewsArticle> findByCategory(String category, Pageable pageable);

    /**
     * 전체 뉴스 목록 (카테고리 필터 없음)
     */
    Page<NewsArticle> findAllByOrderByPublishedAtDesc(Pageable pageable);

    /**
     * URL 중복 체크 (크롤러에서 사용)
     */
    boolean existsBySourceUrl(String sourceUrl);

    Optional<NewsArticle> findBySourceUrl(String sourceUrl);

    /**
     * 오늘 수집된 뉴스 (최대 limit건) — 홈 브리핑용
     * crawledAt >= 오늘 자정 기준
     */
    @Query("SELECT a FROM NewsArticle a WHERE a.crawledAt >= :startOfDay ORDER BY a.publishedAt DESC")
    List<NewsArticle> findTodayArticles(@Param("startOfDay") LocalDateTime startOfDay, Pageable pageable);

    @Modifying
    @Query(value = "UPDATE news_articles SET embedding = CAST(:embedding AS vector) WHERE id = :id",
           nativeQuery = true)
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);
}
