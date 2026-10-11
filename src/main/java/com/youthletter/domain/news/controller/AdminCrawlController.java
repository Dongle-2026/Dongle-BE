package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.service.NewsProcessingService;
import com.youthletter.domain.news.service.NewsProcessingService.CrawlStatus;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin - Crawl", description = "크롤링 관리 API")
@RestController
@RequestMapping("/api/admin/crawl")
@RequiredArgsConstructor
public class AdminCrawlController {

    private final NewsProcessingService newsProcessingService;
    private final AiServiceClient aiServiceClient;
    private final org.springframework.core.env.Environment environment;

    @Operation(summary = "수동 크롤링 트리거", description = "한경 금융 뉴스 즉시 크롤링 → AI 처리 → DB 저장")
    @PostMapping("/trigger")
    public ResponseEntity<GlobalResponse<Map<String, Object>>> trigger() {
        int savedCount = newsProcessingService.processAll();
        return ResponseEntity.ok(GlobalResponse.ok(Map.of(
                "savedCount", savedCount,
                "message", savedCount + "건의 뉴스가 처리되었습니다"
        )));
    }

    @Operation(summary = "크롤링 처리 상태 조회", description = "마지막 실행 시각, 저장 건수, 실패 건수, 상태")
    @GetMapping("/status")
    public ResponseEntity<GlobalResponse<CrawlStatus>> getStatus() {
        return ResponseEntity.ok(GlobalResponse.ok(newsProcessingService.getLastStatus()));
    }

    @Operation(summary = "AI Stub 모드 토글", description = "true면 stub 응답, false면 실제 AI 서비스 호출. 개발 환경 전용.")
    @PostMapping("/ai-stub")
    public ResponseEntity<GlobalResponse<Map<String, Object>>> toggleAiStub() {
        if (!environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test"))) {
            throw new com.youthletter.global.exception.BusinessException(com.youthletter.global.exception.ErrorCode.ACCESS_DENIED);
        }
        boolean newMode = !aiServiceClient.isStubMode();
        aiServiceClient.setStubMode(newMode);
        String message = newMode ? "Stub 모드 활성화 — AI 서비스 없이 동작합니다"
                                 : "실제 AI 서비스 모드 활성화 — localhost:3000 호출합니다";
        return ResponseEntity.ok(GlobalResponse.ok(Map.of("stubMode", newMode, "message", message)));
    }
}
