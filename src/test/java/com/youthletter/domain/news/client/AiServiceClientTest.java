package com.youthletter.domain.news.client;

import com.youthletter.global.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;

class AiServiceClientTest {
    @Test void validationFailureKeepsItsCodeAndIsNotRetried() {
        var client = new AiServiceClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"QUIZ_SELF_CHECK_FAILED\"}").build())).build());
        assertThatThrownBy(() -> client.generateQuiz(nullRequest()))
                .isInstanceOfSatisfying(AiServiceException.class, e -> {
                    assertThat(e.getUpstreamCode()).isEqualTo("QUIZ_SELF_CHECK_FAILED");
                    assertThat(e.isRetryable()).isFalse();
                });
    }
    @Test void timeoutCodeMapsToGatewayTimeout() {
        var client = new AiServiceClient(WebClient.builder().exchangeFunction(request -> {
            assertThat(request.headers().getFirst("X-Request-ID")).hasSize(36);
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Content-Type", "application/json").body("{\"error\":\"OLLAMA_TIMEOUT\"}").build());
        }).build());
        assertThatThrownBy(() -> client.generateCards(nullRequest()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_TIMEOUT));
    }
    private com.youthletter.domain.news.client.dto.AiProcessRequest nullRequest() {
        return new com.youthletter.domain.news.client.dto.AiProcessRequest("url", "title", "content", "YOUTH");
    }
    @Test void arbitraryResponseBodyNeverBecomesLogCode() {
        assertThat(AiServiceClient.safeErrorCode("{\"error\":\"private article or secret\"}"))
                .isEqualTo("AI_UNKNOWN_ERROR");
        assertThat(AiServiceClient.safeErrorCode("not json")).isEqualTo("AI_UNKNOWN_ERROR");
        assertThat(AiServiceClient.safeErrorCode("{\"error\":\"AI_BUSY\"}")).isEqualTo("AI_BUSY");
    }

    private AiServiceClient replying(String summary) {
        String embedding = "[" + String.join(",", java.util.Collections.nCopies(768, "0.01")) + "]";
        return new AiServiceClient(WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"summary\":\"" + summary + "\",\"terms\":[],\"embedding\":" + embedding + "}").build())).build());
    }
    @Test void summaryWithNumberNotInArticleIsRejected() {
        var request = new com.youthletter.domain.news.client.dto.AiProcessRequest("url", "약자 포용", "노인 빈곤율은 40%에 육박한다.", "ECONOMY");
        assertThatThrownBy(() -> replying("인구의 30%가 약자다.").process(request))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AI_SERVICE_ERROR));
        assertThat(replying("노인 빈곤율은 40%에 육박한다.").process(request).summary()).isEqualTo("노인 빈곤율은 40%에 육박한다.");
    }
}
