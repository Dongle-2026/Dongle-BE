package com.youthletter.domain.crawler.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import java.util.List;
import java.util.function.Predicate;

public interface NewsCrawler {
    record Result(List<CrawledArticleDto> articles, int failedCount) {}
    /** 이 수집기를 다시 실행하기까지의 최소 간격. 0이면 스케줄러가 깰 때마다 실행한다. */
    default java.time.Duration interval() {
        return java.time.Duration.ZERO;
    }
    default Result crawlResult(Predicate<String> alreadySaved) {
        return new Result(crawl(alreadySaved), 0);
    }
    List<CrawledArticleDto> crawl(Predicate<String> alreadySaved);
}
