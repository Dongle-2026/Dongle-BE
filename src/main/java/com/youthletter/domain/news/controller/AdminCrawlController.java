package com.youthletter.domain.news.controller;

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
}
