package com.youthletter.domain.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "최근 6개월 소비 트렌드")
public record SpendingTrendResponse(
        @Schema(description = "월별 데이터") List<MonthData> months
) {
    @Schema(description = "월별 소비 데이터")
    public record MonthData(
            @Schema(description = "연월 (2026-04)") String month,
            @Schema(description = "총 지출액") BigDecimal totalAmount,
            @Schema(description = "전월 대비 증감액 (null=첫 달)") BigDecimal changeFromPrev
    ) {}

    public static SpendingTrendResponse from(List<Object[]> rows) {
        List<MonthData> months = rows.stream().map(row -> {
            String month = (String) row[0];
            BigDecimal total = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal prev = row[2] != null ? new BigDecimal(row[2].toString()) : null;
            BigDecimal change = prev != null ? total.subtract(prev) : null;
            return new MonthData(month, total, change);
        }).toList();
        return new SpendingTrendResponse(months);
    }
}
