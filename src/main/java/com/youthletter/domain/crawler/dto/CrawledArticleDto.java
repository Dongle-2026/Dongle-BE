package com.youthletter.domain.crawler.dto;

import java.time.LocalDateTime;

public record CrawledArticleDto(
        String url,
        String title,
        String content,
        String category,
        LocalDateTime publishedAt,
        String thumbnailUrl
) {
}