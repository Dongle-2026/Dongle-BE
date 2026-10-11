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

    @Value("${ai-service.timeout-seconds:370}")
    private long timeoutSeconds = 370;

    public AiCardResponse generateCards(AiProcessRequest request) {
        return post("/api/ai/card-news", request, AiCardResponse.class);
    }

    public com.youthletter.domain.news.client.dto.AiQuizResponse generateQuiz(AiProcessRequest request) {
        return post("/api/ai/quiz", request, com.youthletter.domain.news.client.dto.AiQuizResponse.class);
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
        String requestId = java.util.UUID.randomUUID().toString();
        long started = System.nanoTime();
        try {
            T result = aiServiceWebClient.post().uri(path).header("X-Request-ID", requestId).bodyValue(request).retrieve()
                    .bodyToMono(type).timeout(Duration.ofSeconds(timeoutSeconds)).block();
            if (result == null) throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            log.info("AI request complete: requestId={}, path={}, durationMs={}", requestId, path,
                    (System.nanoTime() - started) / 1_000_000);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            String code = "AI_TRANSPORT_ERROR";
            int status = 0;
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException response) {
                status = response.getStatusCode().value();
                code = safeErrorCode(response.getResponseBodyAsString());
            }
            boolean timeout = "OLLAMA_TIMEOUT".equals(code);
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof java.util.concurrent.TimeoutException) timeout = true;
            }
            log.warn("AI request failed: requestId={}, path={}, status={}, code={}, durationMs={}",
                    requestId, path, status, code, (System.nanoTime() - started) / 1_000_000);
            throw new BusinessException(timeout ? ErrorCode.AI_SERVICE_TIMEOUT : ErrorCode.AI_SERVICE_ERROR);
        }
    }

    static String safeErrorCode(String body) {
        try {
            String code = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("error").asText();
            return java.util.Set.of("OLLAMA_TIMEOUT", "OLLAMA_UNAVAILABLE", "OLLAMA_ERROR", "AI_BUSY",
                    "INVALID_MODEL_JSON", "MODEL_OUTPUT_TRUNCATED", "INVALID_ANALYSIS", "INVALID_TERM", "INVALID_EMBEDDING",
                    "INVALID_CARDS", "INVALID_QUIZ", "UNSUPPORTED_NUMBER", "INVALID_ADVICE", "INVALID_ARTICLE",
                    "INVALID_FACTS", "UNAUTHORIZED").contains(code) ? code : "AI_UNKNOWN_ERROR";
        } catch (Exception ignored) { return "AI_UNKNOWN_ERROR"; }
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
