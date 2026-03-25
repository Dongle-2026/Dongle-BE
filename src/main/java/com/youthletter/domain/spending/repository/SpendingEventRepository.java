package com.youthletter.domain.spending.repository;

import com.youthletter.domain.spending.entity.SpendingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpendingEventRepository extends JpaRepository<SpendingEvent, Long> {

    /**
     * 특정 소비 레코드의 이벤트 이력 (Event Sourcing 재생용)
     */
    List<SpendingEvent> findBySpendingIdOrderByCreatedAtAsc(Long spendingId);

    /**
     * 사용자의 기간별 소비 이벤트 조회
     */
    List<SpendingEvent> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
            Long userId, LocalDate start, LocalDate end);

    /**
     * 스냅샷 생성용: 월별 카테고리 집계
     * 최신 이벤트 기준으로 현재 유효한 소비만 집계
     */
    @Query(value = """
        WITH latest_events AS (
            SELECT DISTINCT ON (spending_id) *
            FROM spending_events
            WHERE user_id = :userId
              AND spent_at BETWEEN :startDate AND :endDate
            ORDER BY spending_id, created_at DESC
        )
        SELECT category, SUM(amount) as total
        FROM latest_events
        WHERE event_type != 'DELETED'
        GROUP BY category
        """, nativeQuery = true)
    List<Object[]> aggregateByCategory(@Param("userId") Long userId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
