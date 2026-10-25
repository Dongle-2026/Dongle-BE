package com.youthletter.domain.news.dto;

import com.youthletter.domain.news.entity.NewsBookmark;

import java.time.LocalDateTime;

/** 저장한 기사. image: 대표 이미지 URL(없으면 생략). */
public record NewsBookmarkResponse(
        Long articleId,
        String title,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        LocalDateTime bookmarkedAt,
        String image
) {
    public static NewsBookmarkResponse from(NewsBookmark bookmark) {
        return new NewsBookmarkResponse(
                bookmark.getArticle().getId(),
                bookmark.getArticle().getTitle(),
                bookmark.getArticle().getSourceName(),
                bookmark.getArticle().getCategory(),
                bookmark.getArticle().getPublishedAt(),
                bookmark.getCreatedAt(),
                bookmark.getArticle().getThumbnailUrl()
        );
    }
}
