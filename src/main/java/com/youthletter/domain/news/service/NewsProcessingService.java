package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.NewsCrawler;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsProcessingService {

    private final List<NewsCrawler> crawlers;
    private final NewsArticleRepository newsArticleRepository;
    private final ContentPipelineStore store;
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();

    public record CrawlStatus(
            LocalDateTime lastRunAt,
            int savedCount,
            int failedCount,
            String status   // NEVER_RUN | RUNNING | SUCCESS | PARTIAL | FAILED
    ) {}

    private final AtomicReference<CrawlStatus> lastStatus =
            new AtomicReference<>(new CrawlStatus(null, 0, 0, "NEVER_RUN"));

    public CrawlStatus getLastStatus() {
        return lastStatus.get();
    }

    /**
     * 6시간마다 자동 실행: 크롤링 -> 원문 저장 및 요약·카드·퀴즈 작업 등록
     */
    @Scheduled(cron = "${crawler.cron:0 0 */6 * * *}")
    public void scheduledProcess() {
        log.info("[Pipeline] 뉴스 크롤링 스케줄 시작");
        processAll();
    }

    /**
     * 크롤링 -> 원문 저장 및 요약·카드·퀴즈 작업 등록 파이프라인.
     * 수동 트리거(AdminCrawlController)에서도 호출.
     */
    public int processAll() {
        if (!running.compareAndSet(false, true)) throw new com.youthletter.global.exception.BusinessException(com.youthletter.global.exception.ErrorCode.CRAWLING_ERROR);
        try {
            lastStatus.set(new CrawlStatus(LocalDateTime.now(), 0, 0, "RUNNING"));

            int savedCount = 0;
            int failedCount = 0;

            for (var crawler : crawlers) {
                List<CrawledArticleDto> articles;
                try {
                    var result = crawler.crawlResult(newsArticleRepository::existsBySourceUrl);
                    articles = result.articles();
                    failedCount += result.failedCount();
                } catch (RuntimeException e) {
                    failedCount++;
                    log.warn("[Pipeline] 수집기 실패: {}", crawler.getClass().getSimpleName(), e);
                    continue;
                }
                for (CrawledArticleDto article : articles) {
                    try {
                        if (newsArticleRepository.existsBySourceUrl(article.url())) {
                            log.debug("[Pipeline] 이미 저장된 기사 스킵: {}", article.url());
                            continue;
                        }

                        store.save(article);
                        log.info("[Pipeline] 저장 완료: {}", article.title());
                        savedCount++;
                    } catch (Exception e) {
                        log.error("[Pipeline] 기사 처리 실패: {} - {}", article.url(), e.getMessage());
                        failedCount++;
                    }
                }

            }
            log.info("[Pipeline] 파이프라인 완료 — 총 {}건 저장, {}건 실패", savedCount, failedCount);
            lastStatus.set(new CrawlStatus(LocalDateTime.now(), savedCount, failedCount,
                    failedCount == 0 ? "SUCCESS" : savedCount > 0 ? "PARTIAL" : "FAILED"));
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
