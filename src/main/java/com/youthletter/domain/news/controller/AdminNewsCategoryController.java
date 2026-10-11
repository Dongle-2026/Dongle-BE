package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.service.NewsCategoryService;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequiredArgsConstructor
public class AdminNewsCategoryController {
    private final NewsCategoryService service;
    private final com.youthletter.domain.news.service.ThumbnailBackfillService thumbnailService;

    @Operation(summary = "기사 카테고리 재분류", description = "전체 기사를 키워드 규칙으로 다시 분류. 바뀐 건수 반환")
    @PostMapping("/api/admin/news/reclassify")
    public GlobalResponse<Map<String, Integer>> reclassify() {
        return GlobalResponse.ok(Map.of("updated", service.reclassifyAll()));
    }

    @Operation(summary = "기사 썸네일 채우기", description = "썸네일 없는 기사 원문에서 og:image 수집. 최대 100건, 요청 간 0.5초 간격")
    @PostMapping("/api/admin/news/thumbnails")
    public GlobalResponse<com.youthletter.domain.news.service.ThumbnailBackfillService.Result> backfillThumbnails(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int limit) {
        return GlobalResponse.ok(thumbnailService.backfill(limit));
    }
}
