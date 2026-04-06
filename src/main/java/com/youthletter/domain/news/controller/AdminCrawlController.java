package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.service.NewsProcessingService;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "수동 크롤링 트리거", description = "한경 금융 뉴스를 즉시 크롤링하고 AI 처리 후 DB에 저장합니다")
    @PostMapping("/trigger")
    public GlobalResponse<Map<String, Object>> trigger() {
        int savedCount = newsProcessingService.processAll();
        return GlobalResponse.ok(Map.of(
                "savedCount", savedCount,
                "message", savedCount + "건의 뉴스가 처리되었습니다"
        ));
    }
}