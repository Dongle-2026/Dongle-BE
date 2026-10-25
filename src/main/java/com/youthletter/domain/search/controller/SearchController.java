package com.youthletter.domain.search.controller;

import com.youthletter.domain.search.dto.CategoryResponse;
import com.youthletter.domain.search.dto.KeywordCardResponse;
import com.youthletter.domain.search.dto.SearchSuggestionResponse;
import com.youthletter.domain.search.dto.TrendingKeywordResponse;
import com.youthletter.domain.search.service.SearchService;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Search", description = "카테고리·키워드·트렌드·자동완성")
@RestController
@RequiredArgsConstructor
public class SearchController {
    private final SearchService searchService;

    @Operation(summary = "뉴스 카테고리 목록", description = "앱 8종. GET /api/news?category= 에 id 사용 가능")
    @GetMapping("/api/categories")
    public GlobalResponse<List<CategoryResponse>> categories() {
        return GlobalResponse.ok(searchService.categories());
    }

    @Operation(summary = "검색 탭 키워드 카드",
            description = "기사에 많이 나온 금융 용어(최근 7일 우선). category(id 또는 이름)를 주면 그 카테고리만. "
                    + "데이터가 모자라면 카테고리별 기본 키워드(10개)로 채운다. limit 기본 10, 최대 20. articleCount는 최근 7일 기사 수")
    @GetMapping("/api/keywords")
    public GlobalResponse<List<KeywordCardResponse>> keywords(@RequestParam(required = false) String category,
                                                              @RequestParam(defaultValue = "10") int limit) {
        return GlobalResponse.ok(searchService.keywordCards(category, limit));
    }

    @Operation(summary = "실시간 트렌드",
            description = "최근 24시간 검색 사용자 수 순위. 검색 기록이 모자라면 기사에 많이 나온 용어로 채워 항상 limit개"
                    + "(기본 8, 최대 20). change: up|down|same")
    @GetMapping("/api/search/trending")
    public GlobalResponse<List<TrendingKeywordResponse>> trending(@RequestParam(defaultValue = "8") int limit) {
        return GlobalResponse.ok(searchService.trending(limit));
    }

    @Operation(summary = "검색어 자동완성", description = "금융 용어 + 인기 검색어")
    @GetMapping("/api/search/suggestions")
    public GlobalResponse<List<SearchSuggestionResponse>> suggestions(@RequestParam String q,
                                                                     @RequestParam(defaultValue = "8") int limit) {
        return GlobalResponse.ok(searchService.suggestions(q, limit));
    }
}
