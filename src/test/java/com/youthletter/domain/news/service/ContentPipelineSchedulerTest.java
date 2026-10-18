package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.HankyungCrawlerService;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContentPipelineSchedulerTest {
    final HankyungCrawlerService crawler = mock(HankyungCrawlerService.class, CALLS_REAL_METHODS);
    final NewsArticleRepository articles = mock(NewsArticleRepository.class);
    final ContentPipelineStore store = mock(ContentPipelineStore.class);
    final ContentPipelineScheduler scheduler = new ContentPipelineScheduler(List.of(crawler), articles, store);

    @Test void crawlFailureIsRecordedInsteadOfReportingEmptySuccess() {
        when(store.start()).thenReturn(1L);
        doThrow(new IllegalStateException("offline")).when(crawler).crawl(any());
        scheduler.collect();
        verify(store).finish(1L, 0, 1, "FAILED");
    }
    @Test void oneBadArticleDoesNotDiscardRemainingArticles() {
        var first=new CrawledArticleDto("a", "a", "a", "금융", null, null);
        var second=new CrawledArticleDto("b", "b", "b", "금융", null, null);
        when(store.start()).thenReturn(1L);
        doReturn(List.of(first, second)).when(crawler).crawl(any());
        when(store.save(first)).thenThrow(new IllegalStateException("invalid article"));
        scheduler.collect();
        verify(store).save(second);
        verify(store).finish(1L, 1, 1, "PARTIAL");
    }
    @Test void failedSourceDoesNotStopOtherSource() {
        var other = mock(HankyungCrawlerService.class, CALLS_REAL_METHODS);
        var article = new CrawledArticleDto("mk", "title", "body", "경제", null, null, "매일경제");
        when(store.start()).thenReturn(1L);
        doThrow(new IllegalStateException("403")).when(crawler).crawl(any());
        doReturn(List.of(article)).when(other).crawl(any());
        new ContentPipelineScheduler(List.of(crawler, other), articles, store).collect();
        verify(store).save(article);
        verify(store).finish(1L, 1, 1, "PARTIAL");
    }
    @Test void parserExclusionsAreRecordedAlongsideSavedArticles() {
        var article = new CrawledArticleDto("mk", "title", "body", "경제", null, null, "매일경제");
        when(store.start()).thenReturn(1L);
        doReturn(new com.youthletter.domain.crawler.service.NewsCrawler.Result(List.of(article), 2))
                .when(crawler).crawlResult(any());
        scheduler.collect();
        verify(store).save(article);
        verify(store).finish(1L, 1, 2, "PARTIAL");
    }

    @Test void eachSourceRunsOnItsOwnInterval() {
        var mk = mock(com.youthletter.domain.crawler.service.NewsCrawler.class, CALLS_REAL_METHODS);
        var hk = mock(com.youthletter.domain.crawler.service.NewsCrawler.class, CALLS_REAL_METHODS);
        when(mk.interval()).thenReturn(java.time.Duration.ofMinutes(30));
        when(hk.interval()).thenReturn(java.time.Duration.ofMinutes(60));
        doReturn(List.of()).when(mk).crawl(any());
        doReturn(List.of()).when(hk).crawl(any());
        when(store.start()).thenReturn(1L);
        var s = new ContentPipelineScheduler(List.of(hk, mk), articles, store);
        var t0 = java.time.Instant.parse("2026-10-19T00:00:00Z");
        s.clock = java.time.Clock.fixed(t0, java.time.ZoneOffset.UTC);
        s.collect();                                   // 시작: 둘 다
        s.clock = java.time.Clock.fixed(t0.plusSeconds(30 * 60 + 5), java.time.ZoneOffset.UTC);
        s.collect();                                   // 30분: 매일경제만
        s.clock = java.time.Clock.fixed(t0.plusSeconds(45 * 60), java.time.ZoneOffset.UTC);
        s.collect();                                   // 45분: 아무것도 안 함(수집 기록도 없음)
        s.clock = java.time.Clock.fixed(t0.plusSeconds(60 * 60 + 10), java.time.ZoneOffset.UTC);
        s.collect();                                   // 60분: 둘 다
        verify(mk, times(3)).crawl(any());
        verify(hk, times(2)).crawl(any());
        verify(store, times(3)).start();
    }
}
