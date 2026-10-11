package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
import com.youthletter.domain.news.entity.*;
import com.youthletter.domain.news.repository.*;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import com.youthletter.global.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsAiWriter {
    private final NewsArticleRepository articles;
    private final NewsSummaryRepository summaries;
    private final FinancialTermRepository terms;
    private final ArticleTermHighlightRepository highlights;
    private final EntityManager entityManager;
    @Transactional
    public Long create(CrawledArticleDto dto, AiProcessResponse result, String model) {
        var article = articles.save(NewsArticle.builder().title(dto.title()).content(dto.content())
                .category(dto.category()).sourceName("한국경제").sourceUrl(dto.url()).publishedAt(dto.publishedAt()).build());
        persist(article, result, model);
        return article.getId();
    }
    @Transactional
    public void replace(Long id, AiProcessResponse result, String model) {
        var article = entityManager.find(NewsArticle.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (article == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        persist(article, result, model);
    }
    private void persist(NewsArticle article, AiProcessResponse result, String model) {
        var history = summaries.findByArticleIdOrderByValidFromDesc(article.getId());
        int version = history.stream().mapToInt(NewsSummary::getSummaryVersion).max().orElse(0) + 1;
        history.stream().filter(s -> s.getValidTo() == null).forEach(NewsSummary::expire);
        summaries.flush();
        summaries.save(NewsSummary.builder().article(article).summaryText(result.summary())
                .summaryVersion(version).modelVersion(model).build());
        entityManager.createQuery("DELETE FROM ArticleTermHighlight h WHERE h.article.id = :id")
                .setParameter("id", article.getId()).executeUpdate();
        if (result.terms() != null) for (var dto : result.terms()) {
            if (dto.term() == null || dto.term().isBlank() || dto.explanation() == null || dto.explanation().isBlank()
                    || dto.startIdx() < 0 || dto.endIdx() <= dto.startIdx() || dto.endIdx() > article.getContent().length()
                    || !article.getContent().substring(dto.startIdx(), dto.endIdx()).equals(dto.term())) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }
            var term = terms.findByName(dto.term()).orElseGet(() -> terms.save(FinancialTerm.builder()
                    .name(dto.term()).path("finance").definition(dto.explanation()).simpleDefinition(dto.explanation())
                    .difficultyLevel(1).build()));
            highlights.save(ArticleTermHighlight.builder().article(article).term(term)
                    .positionStart(dto.startIdx()).positionEnd(dto.endIdx()).build());
        }
        if (result.embedding() != null && result.embedding().stream().anyMatch(v -> v != 0)) {
            articles.updateEmbedding(article.getId(), result.embedding().toString());
        }
    }
}
