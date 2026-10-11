package com.youthletter.domain.news.dto;

import com.youthletter.domain.news.entity.NewsArticle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Schema(description = "뉴스 목록 응답")
@Builder
public record NewsListResponse(
        @Schema(description = "기사 ID") Long id,
        @Schema(description = "제목") String title,
        @Schema(description = "출처") String sourceName,
        @Schema(description = "카테고리") String category,
        @Schema(description = "AI 요약 (3줄)") String summary,
        @Schema(description = "발행일") LocalDateTime publishedAt,
        @Schema(description = "대표 이미지 URL(없으면 생략)") String image,
        @Schema(description = "내가 저장한 기사인지. 비로그인이면 생략") Boolean isSaved
) {
    public NewsListResponse withSaved(Boolean saved) {
        return new NewsListResponse(id, title, sourceName, category, summary, publishedAt, image, saved);
    }

    public static NewsListResponse from(NewsArticle article, String summary) {
        return NewsListResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .sourceName(article.getSourceName())
                .category(article.getCategory())
                .summary(summary)
                .publishedAt(article.getPublishedAt())
                .image(article.getThumbnailUrl())
                .build();
    }
}
