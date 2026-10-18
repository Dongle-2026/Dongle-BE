package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.NewsCrawler;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NewsProcessingServiceTest {
    @Test void manualCrawlQueuesContentsAndSurvivesSourceFailure() {
        var denied = mock(NewsCrawler.class, CALLS_REAL_METHODS);
        var healthy = mock(NewsCrawler.class, CALLS_REAL_METHODS);
        var repository = mock(NewsArticleRepository.class);
        var store = mock(ContentPipelineStore.class);
        var article = new CrawledArticleDto("https://www.mk.co.kr/news/economy/123", "제목", "본문", "경제", null, null, "매일경제");
        when(denied.crawl(any())).thenThrow(new IllegalStateException("403"));
        when(healthy.crawl(any())).thenReturn(List.of(article));
        var service = new NewsProcessingService(List.of(denied, healthy), repository, store);
        assertThat(service.processAll()).isEqualTo(1);
        verify(store).save(article);
        assertThat(service.getLastStatus().status()).isEqualTo("PARTIAL");
        assertThat(service.getLastStatus().failedCount()).isEqualTo(1);
    }
}
