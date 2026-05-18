package com.youthletter.domain.cardnews.dto;

import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import lombok.Builder;

@Builder
public record CardNewsGenerateResponse(
        Long cardNewsId,
        Long articleId,
        int totalCards,
        CardNewsStatus status
) {
    public static CardNewsGenerateResponse from(CardNews cardNews) {
        return CardNewsGenerateResponse.builder()
                .cardNewsId(cardNews.getId())
                .articleId(cardNews.getArticle().getId())
                .totalCards(cardNews.getTotalCards())
                .status(cardNews.getStatus())
                .build();
    }
}
