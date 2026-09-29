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
        @NotNull @Positive @jakarta.validation.constraints.Digits(integer = 10, fraction = 2) BigDecimal amount,

        @Schema(description = "카테고리", example = "FOOD")
        @NotBlank @jakarta.validation.constraints.Size(max = 30) String category,

        @Schema(description = "설명", example = "점심 식사")
        @jakarta.validation.constraints.Size(max = 200) String description,

        @Schema(description = "소비 날짜", example = "2026-03-24")
        @NotNull LocalDate spentAt,

        @Schema(description = "부가 정보 (JSON)")
        String metadata
) {}
