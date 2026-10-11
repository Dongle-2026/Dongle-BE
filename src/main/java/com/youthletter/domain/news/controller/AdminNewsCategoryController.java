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

    @Operation(summary = "기사 카테고리 재분류", description = "전체 기사를 키워드 규칙으로 다시 분류. 바뀐 건수 반환")
    @PostMapping("/api/admin/news/reclassify")
    public GlobalResponse<Map<String, Integer>> reclassify() {
        return GlobalResponse.ok(Map.of("updated", service.reclassifyAll()));
    }
}
