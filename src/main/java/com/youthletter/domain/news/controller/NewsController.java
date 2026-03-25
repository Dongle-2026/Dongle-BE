package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.dto.NewsDetailResponse;
import com.youthletter.domain.news.dto.NewsListResponse;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "News", description = "뉴스 관련 API")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @Operation(summary = "뉴스 목록 조회", description = "카테고리별 뉴스 목록을 페이지네이션으로 조회합니다")
    @GetMapping
    public GlobalResponse<Page<NewsListResponse>> getNewsList(
            @Parameter(description = "카테고리 필터 (ECONOMY, STOCK, INSURANCE, LOAN, POLICY, YOUTH)")
            @RequestParam(required = false) String category,
            @PageableDefault(size = 10, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return GlobalResponse.ok(newsService.getNewsList(category, pageable));
    }

    @Operation(summary = "뉴스 상세 조회", description = "뉴스 원문 + AI 요약 + 용어 하이라이트를 함께 반환합니다")
    @GetMapping("/{articleId}")
    public GlobalResponse<NewsDetailResponse> getNewsDetail(
            @PathVariable Long articleId) {
        return GlobalResponse.ok(newsService.getNewsDetail(articleId));
    }
}
