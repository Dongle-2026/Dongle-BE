package com.youthletter.domain.news.client.dto;

import com.youthletter.domain.cardnews.entity.CardTemplateType;
import java.util.List;

public record AiCardResponse(String modelVersion, String promptVersion, List<Card> cards) {
    public record Card(int cardOrder, CardTemplateType templateType, String title, String body,
                       String subBody, String highlight, List<Integer> sourceSentenceIds) {}
}
