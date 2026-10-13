package com.youthletter.domain.crawler.service;

import com.youthletter.global.exception.BusinessException;
import org.jsoup.HttpStatusException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HankyungCrawlerServiceTest {
    @Test void unavailableListingDoesNotLookLikeSuccessfulEmptyCrawl() throws Exception {
        var crawler = spy(new HankyungCrawlerService());
        doThrow(new HttpStatusException("rate limited",429,"https://www.hankyung.com/economy"))
                .when(crawler).collectArticleUrls();
        assertThatThrownBy(crawler::crawl).isInstanceOf(BusinessException.class);
    }

    @Test void firstRateLimitedArticleStopsWithoutRequestingNext() throws Exception {
        var crawler = spy(new HankyungCrawlerService());
        doReturn(List.of("first", "second")).when(crawler).collectArticleUrls();
        doThrow(new HttpStatusException("rate limited",429,"first")).when(crawler).parseArticle("first");
        assertThatThrownBy(crawler::crawl).isInstanceOf(BusinessException.class);
        verify(crawler, never()).parseArticle("second");
    }

    @Test void allAlreadySavedIsLegitimateEmptyResult() throws Exception {
        var crawler = spy(new HankyungCrawlerService());
        doReturn(List.of("existing")).when(crawler).collectArticleUrls();
        assertThat(crawler.crawl(url -> true)).isEmpty();
        verify(crawler, never()).parseArticle(anyString());
    }
}
