package com.youthletter.domain.stock.controller;

import com.youthletter.domain.stock.dto.StockPriceResponse;
import com.youthletter.domain.stock.dto.StockWatchlistResponse;
import com.youthletter.domain.stock.dto.WatchlistAddRequest;
import com.youthletter.domain.stock.service.StockService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Stock", description = "주식 시세 + 관심종목 API")
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @Operation(summary = "주가 시세 조회", description = "Redis 5분 캐시. stub 모드에서는 랜덤 시세 반환")
    @GetMapping("/{ticker}")
    public GlobalResponse<StockPriceResponse> getStockPrice(@PathVariable String ticker) {
        return GlobalResponse.ok(stockService.getStockPrice(ticker));
    }

    @Operation(summary = "관심종목 추가")
    @PostMapping("/watchlist")
    public GlobalResponse<StockWatchlistResponse> addWatchlist(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody WatchlistAddRequest request) {
        return GlobalResponse.created(stockService.addWatchlist(principal.getUserId(), request));
    }

    @Operation(summary = "관심종목 목록 조회", description = "최근 추가 순")
    @GetMapping("/watchlist")
    public GlobalResponse<List<StockWatchlistResponse>> getWatchlist(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(stockService.getWatchlist(principal.getUserId()));
    }

    @Operation(summary = "관심종목 삭제")
    @DeleteMapping("/watchlist/{ticker}")
    public GlobalResponse<Void> removeWatchlist(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable String ticker) {
        stockService.removeWatchlist(principal.getUserId(), ticker);
        return GlobalResponse.ok("관심종목에서 삭제되었습니다", null);
    }
}
