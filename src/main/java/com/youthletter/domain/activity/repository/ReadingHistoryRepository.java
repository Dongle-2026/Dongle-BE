package com.youthletter.domain.activity.repository;

import com.youthletter.domain.activity.dto.ReadHistoryRow;
import com.youthletter.domain.activity.entity.ReadingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.sql.Date;
import java.util.List;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory> findByUserIdOrderByReadAtDesc(Long userId);

    @Query(value = "SELECT count(DISTINCT (article_id, (read_at AT TIME ZONE 'Asia/Seoul')::date)) FROM reading_history WHERE user_id = :userId", nativeQuery = true)
    long countByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
        INSERT INTO reading_history(user_id, article_id, read_duration_seconds, read_at)
        SELECT :userId, :articleId, :duration, statement_timestamp()
        WHERE NOT EXISTS (
            SELECT 1 FROM reading_history WHERE user_id = :userId AND article_id = :articleId
            AND (read_at AT TIME ZONE 'Asia/Seoul')::date =
                (statement_timestamp() AT TIME ZONE 'Asia/Seoul')::date
        )
        """, nativeQuery = true)
    void insertDailyReading(@Param("userId") Long userId, @Param("articleId") Long articleId,
                            @Param("duration") Integer duration);

    @Query(value = "SELECT DISTINCT (read_at AT TIME ZONE 'Asia/Seoul')::DATE FROM reading_history WHERE user_id = :userId ORDER BY (read_at AT TIME ZONE 'Asia/Seoul')::DATE DESC",
           nativeQuery = true)
    List<Date> findDistinctReadDatesByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT new com.youthletter.domain.activity.dto.ReadHistoryRow(
            a.id, a.title, a.sourceName, a.category, a.publishedAt, max(r.readAt))
        FROM ReadingHistory r JOIN r.article a
        WHERE r.user.id = :userId
        GROUP BY a.id, a.title, a.sourceName, a.category, a.publishedAt
        ORDER BY max(r.readAt) DESC, a.id DESC
        """,
        countQuery = "SELECT count(DISTINCT r.article.id) FROM ReadingHistory r WHERE r.user.id = :userId")
    Page<ReadHistoryRow> findReadHistory(@Param("userId") Long userId, Pageable pageable);
}
