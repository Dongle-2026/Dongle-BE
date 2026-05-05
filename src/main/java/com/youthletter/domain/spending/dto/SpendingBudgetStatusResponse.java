package com.youthletter.domain.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Schema(description = "이번 달 예산 달성률")
public record SpendingBudgetStatusResponse(
        @Schema(description = "월 예산") Long monthlyBudget,
        @Schema(description = "이번 달 총 지출") BigDecimal spentAmount,
        @Schema(description = "예산 대비 지출률 (0~100+)") double usagePercent,
        @Schema(description = "남은 예산 (음수이면 초과)") BigDecimal remaining
) {
    public static SpendingBudgetStatusResponse of(Long budget, BigDecimal spent) {
        BigDecimal budgetDecimal = BigDecimal.valueOf(budget);
        BigDecimal remaining = budgetDecimal.subtract(spent);
        double percent = budget == 0 ? 0
                : spent.multiply(BigDecimal.valueOf(100))
                        .divide(budgetDecimal, 1, RoundingMode.HALF_UP)
                        .doubleValue();
        return new SpendingBudgetStatusResponse(budget, spent, percent, remaining);
    }
}
