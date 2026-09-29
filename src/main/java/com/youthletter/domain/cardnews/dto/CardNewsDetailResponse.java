package com.youthletter.domain.cardnews.dto;

import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record CardNewsDetailResponse(
        Long cardNewsId,
        Long articleId,
        String articleTitle,
        String articleCategory,
        int totalCards,
        CardNewsStatus status,
        LocalDateTime createdAt,
        List<CardNewsCardResponse> cards,
        String modelVersion,
        String promptVersion
) {
    public static CardNewsDetailResponse from(CardNews cardNews) {
        List<CardNewsCardResponse> cardResponses = cardNews.getCards().stream()
                .map(CardNewsCardResponse::from)
                .toList();

        return CardNewsDetailResponse.builder()
                .cardNewsId(cardNews.getId())
                .articleId(cardNews.getArticle().getId())
                .articleTitle(cardNews.getArticle().getTitle())
                .articleCategory(cardNews.getArticle().getCategory())
                .totalCards(cardNews.getTotalCards())
                .status(cardNews.getStatus())
                .createdAt(cardNews.getCreatedAt())
                .cards(cardResponses)
                .modelVersion(cardNews.getModelVersion())
                .promptVersion(cardNews.getPromptVersion())
                .build();
    }
}
