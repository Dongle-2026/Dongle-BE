package com.youthletter.domain.cardnews.service;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class CardNewsValidatorTest {
    private final CardNewsValidator validator = new CardNewsValidator();
    private AiCardResponse response(String body, int source) {
        return new AiCardResponse("qwen3:8b", "card-v1", IntStream.rangeClosed(1,5).mapToObj(i ->
                new AiCardResponse.Card(i, CardTemplateType.T01, "금리", body, null, null, List.of(source))).toList());
    }
    @Test void acceptsGroundedCards() {
        assertThatCode(() -> validator.validate(response("금리는 3%입니다.",1), "금리는 3%입니다.")).doesNotThrowAnyException();
    }
    @Test void rejectsUnknownNumbers() {
        assertThatThrownBy(() -> validator.validate(response("금리는 99%입니다.",1), "금리는 3%입니다.")).isInstanceOf(BusinessException.class);
    }
    @Test void rejectsMissingEvidence() {
        assertThatThrownBy(() -> validator.validate(response("금리 안내",2), "금리는 3%입니다.")).isInstanceOf(BusinessException.class);
    }
}
