package com.youthletter.domain.spending.dto;

import com.youthletter.domain.spending.entity.SpendingEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "소비 내역 목록 응답")
public record SpendingListResponse(
        @Schema(description = "논리적 소비 ID") Long spendingId,
        @Schema(description = "금액") BigDecimal amount,
        @Schema(description = "카테고리") String category,
        @Schema(description = "설명") String description,
        @Schema(description = "소비 날짜") LocalDate spentAt
) {
    public static SpendingListResponse from(SpendingEvent event) {
        return new SpendingListResponse(
                event.getSpendingId(),
                event.getAmount(),
                event.getCategory(),
                event.getDescription(),
                event.getSpentAt()
        );
    }
}
