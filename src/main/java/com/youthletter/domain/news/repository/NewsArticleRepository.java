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

    @Query("""
        SELECT a FROM NewsArticle a WHERE (:category IS NULL OR a.category = :category)
          AND (:keyword IS NULL OR lower(a.title) LIKE lower(concat('%', :keyword, '%'))
               OR lower(a.content) LIKE lower(concat('%', :keyword, '%')))
          AND (cast(:start as timestamp) IS NULL OR a.publishedAt >= :start)
          AND (cast(:end as timestamp) IS NULL OR a.publishedAt < :end)
        """)
    Page<NewsArticle> search(@Param("category") String category, @Param("keyword") String keyword,
                            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

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
