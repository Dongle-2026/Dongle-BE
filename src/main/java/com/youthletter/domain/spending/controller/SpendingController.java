package com.youthletter.domain.spending.controller;

import com.youthletter.domain.spending.dto.*;
import com.youthletter.domain.spending.service.SpendingService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Spending", description = "소비 분석 API (Event Sourcing)")
@RestController
@RequestMapping("/api/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final SpendingService spendingService;

    @Operation(summary = "소비 기록 추가", description = "CREATED 이벤트를 생성합니다 (append-only)")
    @PostMapping
    public GlobalResponse<Long> createSpending(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody SpendingCreateRequest request) {
        return GlobalResponse.created(spendingService.createSpending(principal.getUserId(), request));
    }

    @Operation(summary = "소비 기록 수정", description = "UPDATED 이벤트를 추가합니다 (원본 이벤트는 보존)")
    @PutMapping("/{spendingId}")
    public GlobalResponse<Void> updateSpending(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long spendingId,
            @Valid @RequestBody SpendingCreateRequest request) {
        spendingService.updateSpending(principal.getUserId(), spendingId, request);
        return GlobalResponse.ok();
    }

    @Operation(summary = "소비 기록 삭제", description = "DELETED 이벤트를 추가합니다 (실제 데이터는 삭제되지 않음)")
    @DeleteMapping("/{spendingId}")
    public GlobalResponse<Void> deleteSpending(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long spendingId) {
        spendingService.deleteSpending(principal.getUserId(), spendingId);
        return GlobalResponse.ok();
    }

    @Operation(summary = "월별 소비 목록", description = "해당 월의 유효한 소비 내역 목록 (최신 이벤트 기준, DELETED 제외)")
    @GetMapping
    public GlobalResponse<List<SpendingListResponse>> getMonthlyList(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "조회 연도 (기본: 현재 연도)", example = "2026")
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @Parameter(description = "조회 월 1~12 (기본: 현재 월)", example = "5")
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month) {
        return GlobalResponse.ok(spendingService.getMonthlyList(principal.getUserId(), year, month));
    }

    @Operation(summary = "월별 소비 대시보드", description = "최신 유효 이벤트의 카테고리별 지출 합계")
    @GetMapping("/dashboard")
    public GlobalResponse<SpendingDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Parameter(description = "조회 월 (yyyy-MM 형식, 기본: 현재 월)", example = "2026-05")
            @RequestParam(required = false) String month) {
        if (month == null) month = java.time.YearMonth.now().toString();
        return GlobalResponse.ok(spendingService.getDashboard(principal.getUserId(), month));
    }

    @Operation(summary = "최근 6개월 소비 트렌드", description = "월별 총 지출 + 전월 대비 증감")
    @GetMapping("/trend")
    public GlobalResponse<SpendingTrendResponse> getTrend(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(spendingService.getTrend(principal.getUserId()));
    }

    @Operation(summary = "월 예산 설정 (upsert)", description = "없으면 생성, 있으면 업데이트")
    @PostMapping("/budget")
    public GlobalResponse<Void> upsertBudget(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody SpendingBudgetRequest request) {
        spendingService.upsertBudget(principal.getUserId(), request);
        return GlobalResponse.ok("월 예산이 설정되었습니다", null);
    }

    @Operation(summary = "이번 달 예산 달성률", description = "예산 미설정 시 404")
    @GetMapping("/budget/status")
    public GlobalResponse<SpendingBudgetStatusResponse> getBudgetStatus(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(spendingService.getBudgetStatus(principal.getUserId()));
    }

    @Operation(summary = "AI 소비 인사이트", description = "UTC 당월/전월 실집계와 AI 행동 제안. AI 실패 시 rule-based-v1 반환")
    @PostMapping("/ai-insight")
    public GlobalResponse<SpendingAiInsightResponse> getAiInsight(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(spendingService.getAiInsight(principal.getUserId()));
    }
}
