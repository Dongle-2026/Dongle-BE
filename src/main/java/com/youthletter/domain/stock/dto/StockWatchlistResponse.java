package com.youthletter.domain.stock.dto;

import com.youthletter.domain.stock.entity.StockWatchlist;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관심종목 응답")
public record StockWatchlistResponse(
        @Schema(description = "ID") Long id,
        @Schema(description = "종목 코드") String ticker,
        @Schema(description = "등록일시") LocalDateTime addedAt
) {
    public static StockWatchlistResponse from(StockWatchlist w) {
        return new StockWatchlistResponse(w.getId(), w.getTicker(), w.getCreatedAt());
    }
}
