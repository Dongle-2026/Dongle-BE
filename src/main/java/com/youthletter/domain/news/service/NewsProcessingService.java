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
    private final NewsAiWriter writer;
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();

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
    @Scheduled(cron = "${crawler.cron:0 0 */6 * * *}")
    public void scheduledProcess() {
        log.info("[Pipeline] 뉴스 크롤링 스케줄 시작");
        processAll();
    }

    /**
     * 크롤링 -> AI 처리 -> DB 저장 파이프라인.
     * 수동 트리거(AdminCrawlController)에서도 호출.
     */
    public int processAll() {
        if (!running.compareAndSet(false, true)) throw new com.youthletter.global.exception.BusinessException(com.youthletter.global.exception.ErrorCode.CRAWLING_ERROR);
        try {
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

                writer.create(article, aiResponse, aiServiceClient.isStubMode() ? "stub" : modelVersion);
                log.info("[Pipeline] 저장 완료: {}", article.title());
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
        } catch (RuntimeException e) {
            var previous = lastStatus.get();
            lastStatus.set(new CrawlStatus(LocalDateTime.now(), previous.savedCount(), previous.failedCount(), "FAILED"));
            throw e;
        } finally {
            running.set(false);
        }
    }

}
