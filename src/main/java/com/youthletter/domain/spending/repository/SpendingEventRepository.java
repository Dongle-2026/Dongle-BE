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
    @Query("select e from SpendingEvent e where e.spendingId = :spendingId order by e.createdAt asc, e.id asc")
    List<SpendingEvent> findBySpendingIdOrderByCreatedAtAsc(@Param("spendingId") Long spendingId);

    @Query(value="SELECT nextval('spending_record_id_seq')", nativeQuery=true)
    long nextSpendingId();

    /**
     * 사용자의 기간별 소비 이벤트 조회
     */
    List<SpendingEvent> findByUserIdAndSpentAtBetweenOrderBySpentAtDesc(
            Long userId, LocalDate start, LocalDate end);

    /**
     * 월별 목록: 해당 월의 최신 이벤트 기준 유효 소비 레코드
     */
    @Query(value = """
        SELECT * FROM (
            SELECT DISTINCT ON (spending_id) * FROM spending_events
            WHERE user_id = :userId ORDER BY spending_id, created_at DESC, id DESC
        ) latest
        WHERE spent_at BETWEEN :startDate AND :endDate AND event_type <> 'DELETED'
        ORDER BY spent_at DESC, id DESC
        """, nativeQuery = true)
    List<SpendingEvent> findCurrentStateByUserAndPeriod(@Param("userId") Long userId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);

    /**
     * 최근 6개월 월별 지출 합계 (LAG 윈도우 함수)
     */
    @Query(value = """
        WITH latest AS (
            SELECT DISTINCT ON (spending_id) * FROM spending_events
            WHERE user_id = :userId ORDER BY spending_id, created_at DESC, id DESC
        ), months AS (
            SELECT generate_series(date_trunc('month', CURRENT_DATE) - INTERVAL '5 months',
                                   date_trunc('month', CURRENT_DATE), INTERVAL '1 month') AS month_date
        ), monthly AS (
            SELECT m.month_date, COALESCE(SUM(l.amount), 0) AS total_amount
            FROM months m LEFT JOIN latest l ON l.event_type <> 'DELETED'
              AND l.spent_at >= m.month_date AND l.spent_at < m.month_date + INTERVAL '1 month'
            GROUP BY m.month_date
        )
        SELECT TO_CHAR(month_date, 'YYYY-MM'), total_amount,
               LAG(total_amount) OVER (ORDER BY month_date) FROM monthly ORDER BY month_date
        """, nativeQuery = true)
    List<Object[]> findMonthlyTrend(@Param("userId") Long userId);

    /**
     * 스냅샷 생성용: 월별 카테고리 집계
     * 최신 이벤트 기준으로 현재 유효한 소비만 집계
     */
    @Query(value = """
        WITH latest_events AS (
            SELECT DISTINCT ON (spending_id) *
            FROM spending_events
            WHERE user_id = :userId
            ORDER BY spending_id, created_at DESC, id DESC
        )
        SELECT category, SUM(amount) as total
        FROM latest_events
        WHERE event_type != 'DELETED'
          AND spent_at BETWEEN :startDate AND :endDate
        GROUP BY category
        """, nativeQuery = true)
    List<Object[]> aggregateByCategory(@Param("userId") Long userId,
                                        @Param("startDate") LocalDate startDate,
                                        @Param("endDate") LocalDate endDate);
}
