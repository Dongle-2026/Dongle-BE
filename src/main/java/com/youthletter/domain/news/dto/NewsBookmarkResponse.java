package com.youthletter.domain.news.dto;

import com.youthletter.domain.news.entity.NewsBookmark;

import java.time.LocalDateTime;

public record NewsBookmarkResponse(
        Long articleId,
        String title,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        LocalDateTime bookmarkedAt
) {
    public static NewsBookmarkResponse from(NewsBookmark bookmark) {
        return new NewsBookmarkResponse(
                bookmark.getArticle().getId(),
                bookmark.getArticle().getTitle(),
                bookmark.getArticle().getSourceName(),
                bookmark.getArticle().getCategory(),
                bookmark.getArticle().getPublishedAt(),
                bookmark.getCreatedAt()
        );
    }
}
