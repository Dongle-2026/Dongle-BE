package com.youthletter.domain.news.client;

import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.domain.news.client.dto.AiInsightRequest;
import com.youthletter.domain.news.client.dto.AiInsightResponse;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private final WebClient aiServiceWebClient;

    @Value("${ai-service.use-stub:true}")
    private boolean useStub;

    @Value("${ai-service.timeout-seconds:250}")
    private long timeoutSeconds = 250;

    public AiCardResponse generateCards(AiProcessRequest request) {
        return post("/api/ai/card-news", request, AiCardResponse.class);
    }

    public AiInsightResponse insight(AiInsightRequest request) {
        return post("/api/ai/spending-insight", request, AiInsightResponse.class);
    }

    public Map<String, Object> health() {
        try {
            return aiServiceWebClient.get().uri("/health").retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(5)).block();
        } catch (RuntimeException e) {
            return Map.of("status", "DOWN", "stubMode", useStub);
        }
    }

    private <T> T post(String path, Object request, Class<T> type) {
        try {
            T result = aiServiceWebClient.post().uri(path).bodyValue(request).retrieve()
                    .bodyToMono(type).timeout(Duration.ofSeconds(timeoutSeconds)).block();
            if (result == null) throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI request failed: path={}, type={}", path, e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    public boolean isStubMode() { return useStub; }
    public void setStubMode(boolean stubMode) { this.useStub = stubMode; }

    /**
     * AI Service에 기사를 전송하여 요약 + NER + 임베딩을 받아옴.
     * stub 모드가 true면 AI Service 없이도 크롤링 → DB 저장 흐름 테스트 가능.
     * 혜린이 API 완성 후 application.yml에서 false로 전환.
     */
    public AiProcessResponse process(AiProcessRequest request) {
        if (useStub) {
            log.info("[AiServiceClient] STUB 모드 — AI Service 호출 생략: {}", request.title());
            return createStubResponse(request);
        }

        log.info("[AiServiceClient] AI Service 호출: {}", request.title());

        AiProcessResponse response = post("/api/ai/process", request, AiProcessResponse.class);
        if (response.summary() == null || response.summary().isBlank() || response.embedding() == null
                || response.embedding().size() != 768
                || response.embedding().stream().anyMatch(v -> v == null || !Float.isFinite(v))
                || response.embedding().stream().allMatch(v -> v == 0)) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
        return response;
    }

    /**
     * Stub 응답 생성 (AI Service 없이 파이프라인 테스트용)
     */
    private AiProcessResponse createStubResponse(AiProcessRequest request) {
        String stubSummary = "[STUB] " + request.title() + "에 대한 AI 요약입니다.\n"
                + "이 요약은 테스트용 stub 데이터입니다.\n"
                + "실제 AI Service 연동 후 교체됩니다.";

        return new AiProcessResponse(
                stubSummary,
                List.of(),
                Collections.nCopies(768, 0.0f)
        );
    }
}
