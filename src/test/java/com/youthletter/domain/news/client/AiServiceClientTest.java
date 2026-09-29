package com.youthletter.domain.news.client;

import com.youthletter.global.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import static org.assertj.core.api.Assertions.*;

class AiServiceClientTest {
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
}
