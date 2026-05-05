package com.youthletter.domain.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "관심종목 추가 요청")
public record WatchlistAddRequest(
        @Schema(description = "종목 코드 (예: AAPL, 005930)", example = "AAPL")
        @NotBlank @Size(max = 20) String ticker
) {}
