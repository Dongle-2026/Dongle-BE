package com.youthletter.domain.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "소비 기록 생성/수정 요청")
public record SpendingCreateRequest(
        @Schema(description = "금액", example = "15000")
        @NotNull @Positive BigDecimal amount,

        @Schema(description = "카테고리", example = "FOOD")
        @NotBlank String category,

        @Schema(description = "설명", example = "점심 식사")
        String description,

        @Schema(description = "소비 날짜", example = "2026-03-24")
        @NotNull LocalDate spentAt,

        @Schema(description = "부가 정보 (JSON)")
        String metadata
) {}
