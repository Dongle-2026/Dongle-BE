package com.youthletter.domain.crawler.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import java.util.List;
import java.util.function.Predicate;

public interface NewsCrawler {
    record Result(List<CrawledArticleDto> articles, int failedCount) {}
    default Result crawlResult(Predicate<String> alreadySaved) {
        return new Result(crawl(alreadySaved), 0);
    }
    List<CrawledArticleDto> crawl(Predicate<String> alreadySaved);
}
