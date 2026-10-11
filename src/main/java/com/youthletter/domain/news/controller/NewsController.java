package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.dto.NewsDetailResponse;
import com.youthletter.domain.news.dto.NewsListResponse;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.domain.search.service.SearchService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "News", description = "뉴스 API")
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final SearchService searchService;

    /** 트렌드 집계용: 로그인 사용자의 첫 페이지 검색만 기록(다음 페이지 로딩은 같은 검색). */
    private void recordSearch(CustomUserPrincipal principal, String keyword, Pageable pageable) {
        if (principal != null && pageable.getPageNumber() == 0) searchService.record(principal.getUserId(), keyword);
    }

    @GetMapping("/search")
    public ResponseEntity<GlobalResponse<Page<NewsListResponse>>> searchNews(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        recordSearch(principal, q, pageable);
        return ResponseEntity.ok(GlobalResponse.ok(newsService.getNewsList(null, q, null, pageable)));
    }

    @PostMapping("/{articleId}/save")
    public GlobalResponse<Map<String, Boolean>> saveNews(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                        @PathVariable Long articleId) {
        return saveBookmark(principal, articleId);
    }

    @PostMapping("/{articleId}/unsave")
    public GlobalResponse<Map<String, Boolean>> unsaveNews(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                          @PathVariable Long articleId) {
        return removeBookmark(principal, articleId);
    }

    @Operation(summary = "뉴스 목록 조회", description = "카테고리/페이지네이션")
    @GetMapping
    public ResponseEntity<GlobalResponse<Page<NewsListResponse>>> getNewsList(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "카테고리 id(youth, policy, finance, investment, real-estate, economy, job, saving) 또는 표시명")
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        recordSearch(principal, keyword, pageable);
        return ResponseEntity.ok(GlobalResponse.ok(newsService.getNewsList(category, keyword, date, pageable)));
    }

    @Operation(summary = "오늘의 뉴스 홈 브리핑", description = "오늘 수집된 최신 12건. 로그인 시 관심 카테고리 우선 정렬.")
    @GetMapping("/today")
    public ResponseEntity<GlobalResponse<List<NewsListResponse>>> getTodayNews(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(GlobalResponse.ok(newsService.getTodayNews(userId)));
    }

    @Operation(summary = "뉴스 상세 조회", description = "원문 + AI 요약(stub) + 용어 하이라이트")
    @GetMapping("/{articleId}")
    public ResponseEntity<GlobalResponse<NewsDetailResponse>> getNewsDetail(
            @PathVariable Long articleId) {
        return ResponseEntity.ok(GlobalResponse.ok(newsService.getNewsDetail(articleId)));
    }

    @Operation(summary = "뉴스 읽음 처리", description = "읽기 이력 저장. durationSeconds는 선택.")
    @PostMapping("/{articleId}/read")
    public ResponseEntity<GlobalResponse<Void>> markAsRead(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long articleId,
            @RequestParam(required = false) Integer durationSeconds) {
        newsService.markAsRead(principal.getUserId(), articleId, durationSeconds);
        return ResponseEntity.ok(GlobalResponse.ok("읽음 처리 완료", null));
    }

    @Operation(summary = "뉴스 북마크 토글", description = "북마크 추가 시 true, 해제 시 false 반환")
    @PostMapping("/{articleId}/bookmark")
    public ResponseEntity<GlobalResponse<Map<String, Boolean>>> toggleBookmark(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long articleId) {
        boolean bookmarked = newsService.toggleBookmark(principal.getUserId(), articleId);
        return ResponseEntity.ok(GlobalResponse.ok(Map.of("bookmarked", bookmarked)));
    }

    @GetMapping("/{articleId}/bookmark")
    public GlobalResponse<Map<String, Boolean>> bookmarkStatus(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long articleId) {
        return GlobalResponse.ok(Map.of("bookmarked", newsService.isBookmarked(principal.getUserId(), articleId)));
    }
    @PutMapping("/{articleId}/bookmark")
    public GlobalResponse<Map<String, Boolean>> saveBookmark(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long articleId) {
        return GlobalResponse.ok(Map.of("bookmarked", newsService.setBookmark(principal.getUserId(), articleId, true)));
    }
    @DeleteMapping("/{articleId}/bookmark")
    public GlobalResponse<Map<String, Boolean>> removeBookmark(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long articleId) {
        return GlobalResponse.ok(Map.of("bookmarked", newsService.setBookmark(principal.getUserId(), articleId, false)));
    }
}
