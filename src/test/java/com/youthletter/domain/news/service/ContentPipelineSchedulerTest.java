package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.HankyungCrawlerService;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ContentPipelineSchedulerTest {
    final HankyungCrawlerService crawler = mock(HankyungCrawlerService.class);
    final NewsArticleRepository articles = mock(NewsArticleRepository.class);
    final ContentPipelineStore store = mock(ContentPipelineStore.class);
    final ContentPipelineScheduler scheduler = new ContentPipelineScheduler(crawler, articles, store);

    @Test void crawlFailureIsRecordedInsteadOfReportingEmptySuccess() {
        when(store.start()).thenReturn(1L);
        when(crawler.crawl(any())).thenThrow(new IllegalStateException("offline"));
        scheduler.collect();
        verify(store).finish(1L, 0, 1, "FAILED");
    }
    @Test void oneBadArticleDoesNotDiscardRemainingArticles() {
        var first=new CrawledArticleDto("a", "a", "a", "금융", null, null);
        var second=new CrawledArticleDto("b", "b", "b", "금융", null, null);
        when(store.start()).thenReturn(1L);
        when(crawler.crawl(any())).thenReturn(List.of(first, second));
        when(store.save(first)).thenThrow(new IllegalStateException("invalid article"));
        scheduler.collect();
        verify(store).save(second);
        verify(store).finish(1L, 1, 1, "PARTIAL");
    }
}
