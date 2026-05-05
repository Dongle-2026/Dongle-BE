package com.youthletter.domain.spending.controller;

import com.youthletter.domain.spending.dto.SpendingCreateRequest;
import com.youthletter.domain.spending.dto.SpendingDashboardResponse;
import com.youthletter.domain.spending.service.SpendingService;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@Tag(name = "Spending", description = "소비 분석 API (Event Sourcing)")
@RestController
@RequestMapping("/api/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final SpendingService spendingService;

    @Operation(summary = "소비 기록 추가", description = "CREATED 이벤트를 생성합니다 (append-only)")
    @PostMapping
    public GlobalResponse<Long> createSpending(
            @RequestParam Long userId,  // TODO: SecurityContext에서 추출
            @Valid @RequestBody SpendingCreateRequest request) {
        Long spendingId = spendingService.createSpending(userId, request);
        return GlobalResponse.created(spendingId);
    }

    @Operation(summary = "소비 기록 수정", description = "UPDATED 이벤트를 추가합니다 (원본 이벤트는 보존)")
    @PutMapping("/{spendingId}")
    public GlobalResponse<Void> updateSpending(
            @RequestParam Long userId,
            @PathVariable Long spendingId,
            @Valid @RequestBody SpendingCreateRequest request) {
        spendingService.updateSpending(userId, spendingId, request);
        return GlobalResponse.ok();
    }

    @Operation(summary = "소비 기록 삭제", description = "DELETED 이벤트를 추가합니다 (실제 데이터는 삭제되지 않음)")
    @DeleteMapping("/{spendingId}")
    public GlobalResponse<Void> deleteSpending(
            @RequestParam Long userId,
            @PathVariable Long spendingId) {
        spendingService.deleteSpending(userId, spendingId);
        return GlobalResponse.ok();
    }

    @Operation(summary = "월별 소비 대시보드", description = "카테고리별 지출 합계를 반환합니다 (스냅샷 기반)")
    @GetMapping("/dashboard")
    public GlobalResponse<SpendingDashboardResponse> getDashboard(
            @RequestParam Long userId,
            @RequestParam(required = false) String month) {
        if (month == null) month = YearMonth.now().toString();
        return GlobalResponse.ok(spendingService.getDashboard(userId, month));
    }
}
