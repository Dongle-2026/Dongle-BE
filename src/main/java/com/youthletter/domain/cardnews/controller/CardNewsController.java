package com.youthletter.domain.cardnews.controller;

import com.youthletter.domain.cardnews.dto.CardNewsDetailResponse;
import com.youthletter.domain.cardnews.dto.CardNewsGenerateResponse;
import com.youthletter.domain.cardnews.dto.CardNewsListResponse;
import com.youthletter.domain.cardnews.service.CardNewsService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "CardNews", description = "카드뉴스 API")
@RestController
@RequestMapping("/api/card-news")
@RequiredArgsConstructor
public class CardNewsController {

    private final CardNewsService cardNewsService;

    @Operation(
        summary = "카드뉴스 생성",
        description = "뉴스 기사를 기반으로 카드뉴스를 생성한다. 이미 생성된 경우 재생성한다. ADMIN 권한 필요."
    )
    @PostMapping("/{articleId}/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GlobalResponse<CardNewsGenerateResponse>> generate(
            @Parameter(description = "뉴스 기사 ID") @PathVariable Long articleId) {
        return ResponseEntity.ok(GlobalResponse.ok(
                "카드뉴스 생성이 완료되었습니다.",
                cardNewsService.generate(articleId)));
    }

    @Operation(
        summary = "카드뉴스 목록 조회",
        description = "게시된 카드뉴스 목록을 페이지네이션으로 반환한다."
    )
    @GetMapping
    public ResponseEntity<GlobalResponse<Page<CardNewsListResponse>>> getList(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(GlobalResponse.ok(cardNewsService.getList(pageable)));
    }

    @Operation(
        summary = "카드뉴스 상세 조회 (기사 ID 기준)",
        description = "뉴스 기사 ID로 해당 기사의 카드뉴스 전체를 조회한다."
    )
    @GetMapping("/article/{articleId}")
    public ResponseEntity<GlobalResponse<CardNewsDetailResponse>> getByArticle(
            @Parameter(description = "뉴스 기사 ID") @PathVariable Long articleId) {
        return ResponseEntity.ok(GlobalResponse.ok(cardNewsService.getDetail(articleId)));
    }

    @Operation(
        summary = "카드뉴스 상세 조회 (카드뉴스 ID 기준)",
        description = "카드뉴스 ID로 카드뉴스 전체를 조회한다."
    )
    @GetMapping("/{cardNewsId}")
    public ResponseEntity<GlobalResponse<CardNewsDetailResponse>> getById(
            @Parameter(description = "카드뉴스 ID") @PathVariable Long cardNewsId) {
        return ResponseEntity.ok(GlobalResponse.ok(cardNewsService.getDetailById(cardNewsId)));
    }
}
