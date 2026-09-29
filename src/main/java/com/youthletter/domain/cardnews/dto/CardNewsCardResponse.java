package com.youthletter.domain.cardnews.dto;

import com.youthletter.domain.cardnews.entity.CardNewsCard;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import lombok.Builder;

@Builder
public record CardNewsCardResponse(
        Long id,
        int cardOrder,
        CardTemplateType templateType,
        String templateLabel,
        String title,
        String body,
        String subBody,
        String highlight,
        String imageUrl,
        java.util.List<Integer> sourceSentenceIds
) {
    public static CardNewsCardResponse from(CardNewsCard card) {
        return CardNewsCardResponse.builder()
                .id(card.getId())
                .cardOrder(card.getCardOrder())
                .templateType(card.getTemplateType())
                .templateLabel(card.getTemplateType().getLabel())
                .title(card.getTitle())
                .body(card.getBody())
                .subBody(card.getSubBody())
                .highlight(card.getHighlight())
                .imageUrl(card.getImageUrl())
                .sourceSentenceIds(card.getSourceSentenceIds())
                .build();
    }
}
