package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.service.NewsCrawler;
import java.util.List;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 전용 단일 인스턴스 실행 환경: 시작 직후 수집하고 이후 주기적으로 반복한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="content-pipeline.enabled", havingValue="true")
public class ContentPipelineScheduler {
    private final List<NewsCrawler> crawlers;
    private final NewsArticleRepository articles;
    private final ContentPipelineStore store;

    @Scheduled(initialDelayString="${content-pipeline.initial-delay-ms:10000}",
            fixedDelayString="${content-pipeline.interval-ms:21600000}")
    public void collect() {
        Long run = store.start();
        int saved = 0;
        int failed = 0;
        for (var crawler : crawlers) {
            try {
                var result = crawler.crawlResult(articles::existsBySourceUrl);
                failed += result.failedCount();
                for (var article : result.articles()) {
                    try {
                        store.save(article);
                        saved++;
                    } catch (RuntimeException e) {
                        failed++;
                        log.warn("[ContentPipeline] 기사 저장 실패: {}", article.url(), e);
                    }
                }

            } catch (RuntimeException e) {
                failed++;
                log.error("[ContentPipeline] 수집 실패. 다음 주기에 재시도합니다.", e);
            }
        }
        store.finish(run, saved, failed, failed == 0 ? "SUCCEEDED" : saved > 0 ? "PARTIAL" : "FAILED");
        log.info("[ContentPipeline] 수집 종료: 저장={}, 실패={}; AI 결과는 대기열에서 순서대로 저장합니다.", saved, failed);
    }
}
