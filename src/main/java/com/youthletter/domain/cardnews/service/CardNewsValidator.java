package com.youthletter.domain.cardnews.service;

import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.global.exception.*;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class CardNewsValidator {
    public void validate(AiCardResponse result, String content) {
        if (result == null || !text(result.modelVersion(), 100) || !text(result.promptVersion(), 100)
                || result.cards() == null || result.cards().size() < 5 || result.cards().size() > 7) fail();
        var sentences = Arrays.stream(content.split("(?<=[.!?。])\\s+|\\n+"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        for (int i = 0; i < result.cards().size(); i++) {
            var c = result.cards().get(i);
            if (c == null || c.cardOrder() != i + 1 || c.templateType() == null
                    || !text(c.title(), 50) || !text(c.body(), 200)
                    || (c.subBody() != null && c.subBody().length() > 200)
                    || (c.highlight() != null && c.highlight().length() > 100)
                    || c.sourceSentenceIds() == null || c.sourceSentenceIds().isEmpty()
                    || c.sourceSentenceIds().stream().anyMatch(id -> id == null || id < 1 || id > sentences.size())) fail();
            String evidence = c.sourceSentenceIds().stream().map(id -> sentences.get(id - 1)).collect(java.util.stream.Collectors.joining(" "));
            if (!com.youthletter.global.util.NumberEvidence.unsupported(
                    c.title() + " " + c.body() + " " + (c.subBody() == null ? "" : c.subBody()), evidence).isEmpty()) fail();
        }
    }
    private boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private void fail() { throw new BusinessException(ErrorCode.AI_SERVICE_ERROR); }
}
