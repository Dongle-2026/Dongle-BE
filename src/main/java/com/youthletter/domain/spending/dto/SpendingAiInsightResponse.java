package com.youthletter.domain.spending.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "AI 소비 인사이트 응답")
public record SpendingAiInsightResponse(
        @Schema(description = "분석 시작일") LocalDate analysisStart,
        @Schema(description = "분석 종료일") LocalDate analysisEnd,
        @Schema(description = "인사이트 텍스트") String insightText,
        @Schema(description = "모델 버전") String modelVersion
) {
    public static SpendingAiInsightResponse stub(LocalDate start, LocalDate end) {
        return new SpendingAiInsightResponse(
                start, end,
                "이번 달 식비 지출이 지난달 대비 15% 증가했어요. 외식 빈도를 줄이면 월 3만원 절약 가능해요! " +
                "교통비는 안정적으로 유지되고 있으며, 전체 지출 중 저축 여력은 약 22%로 추정돼요.",
                "stub-v1.0"
        );
    }
}
