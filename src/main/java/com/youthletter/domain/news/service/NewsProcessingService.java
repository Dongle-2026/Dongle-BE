package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.HankyungCrawlerService;
import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
import com.youthletter.domain.news.entity.ArticleTermHighlight;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.entity.NewsSummary;
import com.youthletter.domain.news.repository.ArticleTermHighlightRepository;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.news.repository.NewsSummaryRepository;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsProcessingService {

    private final HankyungCrawlerService crawlerService;
    private final AiServiceClient aiServiceClient;
    private final NewsArticleRepository newsArticleRepository;
    private final NewsSummaryRepository newsSummaryRepository;
    private final FinancialTermRepository financialTermRepository;
    private final ArticleTermHighlightRepository articleTermHighlightRepository;

    @Value("${ai-service.model-version:qwen3:8b}")
    private String modelVersion;

    public record CrawlStatus(
            LocalDateTime lastRunAt,
            int savedCount,
            int failedCount,
            String status   // NEVER_RUN | RUNNING | SUCCESS | FAILED
    ) {}

    private final AtomicReference<CrawlStatus> lastStatus =
            new AtomicReference<>(new CrawlStatus(null, 0, 0, "NEVER_RUN"));

    public CrawlStatus getLastStatus() {
        return lastStatus.get();
    }

    /**
     * 6시간마다 자동 실행: 크롤링 -> AI 처리 -> DB 저장
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledProcess() {
        log.info("[Pipeline] 뉴스 크롤링 스케줄 시작");
        processAll();
    }

    /**
     * 크롤링 -> AI 처리 -> DB 저장 파이프라인.
     * 수동 트리거(AdminCrawlController)에서도 호출.
     */
    @Transactional
    public int processAll() {
        lastStatus.set(new CrawlStatus(LocalDateTime.now(), 0, 0, "RUNNING"));

        List<CrawledArticleDto> articles = crawlerService.crawl();
        int savedCount = 0;
        int failedCount = 0;

        for (CrawledArticleDto article : articles) {
            try {
                if (newsArticleRepository.existsBySourceUrl(article.url())) {
                    log.debug("[Pipeline] 이미 저장된 기사 스킵: {}", article.url());
                    continue;
                }

                AiProcessResponse aiResponse = aiServiceClient.process(
                        new AiProcessRequest(article.url(), article.title(),
                                article.content(), article.category()));

                NewsArticle saved = newsArticleRepository.save(
                        NewsArticle.builder()
                                .sourceUrl(article.url())
                                .sourceName("한국경제")
                                .title(article.title())
                                .content(article.content())
                                .category(article.category())
                                .publishedAt(article.publishedAt())
                                .build()
                );

                if (aiResponse.summary() != null && !aiResponse.summary().isBlank()) {
                    newsSummaryRepository.save(
                            NewsSummary.builder()
                                    .article(saved)
                                    .summaryText(aiResponse.summary())
                                    .summaryVersion(1)
                                    .modelVersion(modelVersion)
                                    .build()
                    );
                }

                saveTermHighlights(saved, aiResponse.terms());
                saveEmbedding(saved.getId(), aiResponse.embedding());

                log.info("[Pipeline] 저장 완료: {}", saved.getTitle());
                savedCount++;
            } catch (Exception e) {
                log.error("[Pipeline] 기사 처리 실패: {} - {}", article.url(), e.getMessage());
                failedCount++;
            }
        }

        log.info("[Pipeline] 파이프라인 완료 — 총 {}건 저장, {}건 실패", savedCount, failedCount);
        lastStatus.set(new CrawlStatus(LocalDateTime.now(), savedCount, failedCount,
                failedCount == 0 ? "SUCCESS" : "PARTIAL"));
        return savedCount;
    }

    private void saveTermHighlights(NewsArticle article, List<AiProcessResponse.TermDto> terms) {
        if (terms == null || terms.isEmpty()) return;
        for (AiProcessResponse.TermDto dto : terms) {
            try {
                FinancialTerm term = financialTermRepository.findByName(dto.term())
                        .orElseGet(() -> financialTermRepository.save(
                                FinancialTerm.builder()
                                        .name(dto.term())
                                        .path("finance")
                                        .definition(dto.explanation())
                                        .simpleDefinition(dto.explanation())
                                        .difficultyLevel(1)
                                        .build()
                        ));
                articleTermHighlightRepository.save(
                        ArticleTermHighlight.builder()
                                .article(article)
                                .term(term)
                                .positionStart(dto.startIdx())
                                .positionEnd(dto.endIdx())
                                .build()
                );
            } catch (Exception e) {
                log.warn("[Pipeline] 용어 저장 실패: {} - {}", dto.term(), e.getMessage());
            }
        }
    }

    private void saveEmbedding(Long articleId, List<Float> embedding) {
        if (embedding == null || embedding.isEmpty()) return;
        try {
            newsArticleRepository.updateEmbedding(articleId, embedding.toString());
        } catch (Exception e) {
            log.warn("[Pipeline] 임베딩 저장 실패: articleId={} - {}", articleId, e.getMessage());
        }
    }
}