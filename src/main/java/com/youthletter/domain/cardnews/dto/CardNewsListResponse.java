package com.youthletter.domain.cardnews.dto;

import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CardNewsListResponse(
        Long cardNewsId,
        Long articleId,
        String articleTitle,
        String articleCategory,
        int totalCards,
        CardNewsStatus status,
        LocalDateTime createdAt
) {
    public static CardNewsListResponse from(CardNews cardNews) {
        return CardNewsListResponse.builder()
                .cardNewsId(cardNews.getId())
                .articleId(cardNews.getArticle().getId())
                .articleTitle(cardNews.getArticle().getTitle())
                .articleCategory(cardNews.getArticle().getCategory())
                .totalCards(cardNews.getTotalCards())
                .status(cardNews.getStatus())
                .createdAt(cardNews.getCreatedAt())
                .build();
    }
}
