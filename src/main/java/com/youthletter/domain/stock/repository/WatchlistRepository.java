package com.youthletter.domain.stock.repository;

import com.youthletter.domain.stock.entity.StockWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<StockWatchlist, Long> {

    List<StockWatchlist> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<StockWatchlist> findByUserIdAndTicker(Long userId, String ticker);

    boolean existsByUserIdAndTicker(Long userId, String ticker);
}
