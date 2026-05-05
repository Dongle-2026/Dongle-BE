package com.youthletter.domain.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "월 예산 설정 요청")
public record SpendingBudgetRequest(
        @Schema(description = "월 예산 (원)", example = "500000")
        @NotNull @Min(0) Long monthlyBudget
) {}
