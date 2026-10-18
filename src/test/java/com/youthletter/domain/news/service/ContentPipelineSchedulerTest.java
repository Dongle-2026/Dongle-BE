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
}
