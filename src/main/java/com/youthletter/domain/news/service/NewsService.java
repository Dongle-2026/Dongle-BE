package com.youthletter.domain.news.service;

import com.youthletter.domain.news.dto.NewsDetailResponse;
import com.youthletter.domain.news.dto.NewsDetailResponse.TermHighlightDto;
import com.youthletter.domain.news.dto.NewsListResponse;
import com.youthletter.domain.news.entity.ArticleTermHighlight;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.entity.NewsSummary;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.news.repository.NewsSummaryRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsArticleRepository articleRepository;
    private final NewsSummaryRepository summaryRepository;
    private final EntityManager entityManager;

    /**
     * 뉴스 목록 조회 (홈 화면)
     */
    public Page<NewsListResponse> getNewsList(String category, Pageable pageable) {
        Page<NewsArticle> articles;

        if (category != null && !category.isBlank()) {
            articles = articleRepository.findByCategory(category, pageable);
        } else {
            articles = articleRepository.findAllByOrderByPublishedAtDesc(pageable);
        }

        return articles.map(article -> {
            String summary = summaryRepository
                    .findByArticleIdAndValidToIsNull(article.getId())
                    .map(NewsSummary::getSummaryText)
                    .orElse(null);
            return NewsListResponse.from(article, summary);
        });
    }

    /**
     * 뉴스 상세 조회 (원문 + 요약 + 용어 하이라이트)
     */
    public NewsDetailResponse getNewsDetail(Long articleId) {
        NewsArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 현재 유효한 요약
        NewsSummary summary = summaryRepository
                .findByArticleIdAndValidToIsNull(articleId)
                .orElse(null);

        // 용어 하이라이트 (JPQL로 fetch join)
        List<TermHighlightDto> highlights = entityManager
                .createQuery("""
                    SELECT h FROM ArticleTermHighlight h
                    JOIN FETCH h.term
                    WHERE h.article.id = :articleId
                    ORDER BY h.positionStart ASC
                    """, ArticleTermHighlight.class)
                .setParameter("articleId", articleId)
                .getResultList()
                .stream()
                .map(h -> TermHighlightDto.builder()
                        .termId(h.getTerm().getId())
                        .termName(h.getTerm().getName())
                        .simpleDefinition(h.getTerm().getSimpleDefinition())
                        .positionStart(h.getPositionStart())
                        .positionEnd(h.getPositionEnd())
                        .build())
                .toList();

        return NewsDetailResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .sourceName(article.getSourceName())
                .sourceUrl(article.getSourceUrl())
                .category(article.getCategory())
                .publishedAt(article.getPublishedAt())
                .summary(summary != null ? summary.getSummaryText() : null)
                .modelVersion(summary != null ? summary.getModelVersion() : null)
                .termHighlights(highlights)
                .build();
    }
}
