package com.youthletter.domain.news.client;

import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
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

        return aiServiceWebClient.post()
                .uri("/api/ai/process")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiProcessResponse.class)
                .block();
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
                Collections.nCopies(1536, 0.0f)
        );
    }
}