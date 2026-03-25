package com.youthletter.domain.news.repository;

import com.youthletter.domain.news.entity.NewsSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NewsSummaryRepository extends JpaRepository<NewsSummary, Long> {

    /**
     * 현재 유효한 요약 조회 (valid_to IS NULL)
     * 부분 인덱스 idx_summaries_article_valid 활용
     */
    Optional<NewsSummary> findByArticleIdAndValidToIsNull(Long articleId);

    /**
     * 특정 기사의 전체 요약 이력 (버전 관리 확인용)
     */
    List<NewsSummary> findByArticleIdOrderByValidFromDesc(Long articleId);

    /**
     * 요약이 아직 생성되지 않은 기사 ID 목록 (배치 요약 생성용)
     */
    @Query("SELECT a.id FROM NewsArticle a WHERE a.id NOT IN " +
           "(SELECT DISTINCT s.article.id FROM NewsSummary s WHERE s.validTo IS NULL)")
    List<Long> findArticleIdsWithoutActiveSummary();
}
