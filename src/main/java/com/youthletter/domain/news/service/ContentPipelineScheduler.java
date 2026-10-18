package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.service.NewsCrawler;
import java.util.List;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 전용 단일 인스턴스 실행 환경: 시작 직후 모든 수집기를 실행하고, 이후 interval-ms마다 깨어나
 * 각 수집기의 주기(NewsCrawler.interval, 예: 매일경제 30분·한경 1시간)가 지난 것만 실행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="content-pipeline.enabled", havingValue="true")
public class ContentPipelineScheduler {
    private final List<NewsCrawler> crawlers;
    private final NewsArticleRepository articles;
    private final ContentPipelineStore store;
    /** 수집기별 마지막 시작 시각. 재시작하면 비어 있어 모두 한 번 실행한다. */
    private final java.util.Map<NewsCrawler, java.time.Instant> lastStarted = new java.util.IdentityHashMap<>();
    java.time.Clock clock = java.time.Clock.systemUTC();

    @Scheduled(initialDelayString="${content-pipeline.initial-delay-ms:10000}",
            fixedDelayString="${content-pipeline.interval-ms:1800000}")
    public void collect() {
        var now = clock.instant();
        var due = crawlers.stream().filter(c -> {
            var last = lastStarted.get(c);
            return last == null || !now.isBefore(last.plus(c.interval()));
        }).toList();
        if (due.isEmpty()) return;
        due.forEach(c -> lastStarted.put(c, now));
        Long run = store.start();
        int saved = 0;
        int failed = 0;
        for (var crawler : due) {
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
