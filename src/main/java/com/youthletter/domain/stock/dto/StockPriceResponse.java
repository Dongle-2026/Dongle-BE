package com.youthletter.domain.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "주가 시세 응답")
public record StockPriceResponse(
        @Schema(description = "종목 코드") String ticker,
        @Schema(description = "종목명") String name,
        @Schema(description = "현재가") BigDecimal price,
        @Schema(description = "전일 대비 변동액") BigDecimal change,
        @Schema(description = "전일 대비 변동률 (%)") double changePercent,
        @Schema(description = "거래량") Long volume,
        @Schema(description = "조회 시각") LocalDateTime retrievedAt,
        @Schema(description = "stub 모드 여부") boolean isStub
) {}
