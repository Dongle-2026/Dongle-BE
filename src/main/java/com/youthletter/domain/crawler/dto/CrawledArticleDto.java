package com.youthletter.domain.crawler.dto;

import java.time.LocalDateTime;

public record CrawledArticleDto(
        String url,
        String title,
        String content,
        String category,
        LocalDateTime publishedAt,
        String thumbnailUrl,
        String sourceName
) {
    public CrawledArticleDto(String url, String title, String content, String category,
                             LocalDateTime publishedAt, String thumbnailUrl) {
        this(url, title, content, category, publishedAt, thumbnailUrl, "한국경제");
    }
}