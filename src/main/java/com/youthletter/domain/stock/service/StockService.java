package com.youthletter.domain.stock.service;

import com.youthletter.domain.stock.dto.StockPriceResponse;
import com.youthletter.domain.stock.dto.StockWatchlistResponse;
import com.youthletter.domain.stock.dto.WatchlistAddRequest;
import com.youthletter.domain.stock.entity.StockWatchlist;
import com.youthletter.domain.stock.repository.WatchlistRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final String CACHE_PREFIX = "STOCK:";
    private static final long CACHE_TTL_MINUTES = 5;

    private static final Map<String, String> KNOWN_TICKERS = Map.of(
            "AAPL", "Apple Inc.",
            "TSLA", "Tesla, Inc.",
            "NVDA", "NVIDIA Corp.",
            "MSFT", "Microsoft Corp.",
            "005930", "삼성전자",
            "000660", "SK하이닉스",
            "035720", "카카오",
            "035420", "NAVER"
    );

    private final WatchlistRepository watchlistRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // ── 시세 조회 ─────────────────────────────────────────────────────────────

    public StockPriceResponse getStockPrice(String ticker) {
        String cacheKey = CACHE_PREFIX + ticker.toUpperCase();
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof StockPriceResponse cachedPrice) {
            return cachedPrice;
        }

        StockPriceResponse price = fetchStubPrice(ticker.toUpperCase());

        redisTemplate.opsForValue().set(cacheKey, price, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return price;
    }

    // ── 관심종목 CRUD ──────────────────────────────────────────────────────────

    @Transactional
    public StockWatchlistResponse addWatchlist(Long userId, WatchlistAddRequest request) {
        String ticker = request.ticker().toUpperCase();

        if (watchlistRepository.existsByUserIdAndTicker(userId, ticker)) {
            throw new BusinessException(ErrorCode.WATCHLIST_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        StockWatchlist saved = watchlistRepository.save(
                StockWatchlist.builder().user(user).ticker(ticker).build()
        );
        return StockWatchlistResponse.from(saved);
    }

    public List<StockWatchlistResponse> getWatchlist(Long userId) {
        return watchlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(StockWatchlistResponse::from)
                .toList();
    }

    @Transactional
    public void removeWatchlist(Long userId, String ticker) {
        StockWatchlist item = watchlistRepository.findByUserIdAndTicker(userId, ticker.toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.WATCHLIST_NOT_FOUND));
        watchlistRepository.delete(item);
    }

    // ── stub 가격 생성 ─────────────────────────────────────────────────────────

    private StockPriceResponse fetchStubPrice(String ticker) {
        String name = KNOWN_TICKERS.getOrDefault(ticker, ticker + " (Unknown)");
        boolean isKorean = ticker.matches("\\d{6}");

        BigDecimal basePrice = isKorean
                ? BigDecimal.valueOf(ticker.equals("005930") ? 75000 : 130000)
                : BigDecimal.valueOf(ticker.equals("AAPL") ? 175.0 : 250.0);

        double jitter = ThreadLocalRandom.current().nextDouble(-0.03, 0.03);
        BigDecimal price = basePrice.multiply(BigDecimal.valueOf(1 + jitter))
                .setScale(isKorean ? 0 : 2, java.math.RoundingMode.HALF_UP);
        BigDecimal change = basePrice.multiply(BigDecimal.valueOf(jitter))
                .setScale(isKorean ? 0 : 2, java.math.RoundingMode.HALF_UP);
        double changePercent = Math.round(jitter * 10000.0) / 100.0;

        return new StockPriceResponse(
                ticker, name, price, change, changePercent,
                ThreadLocalRandom.current().nextLong(100_000, 10_000_000),
                LocalDateTime.now(), true
        );
    }
}
