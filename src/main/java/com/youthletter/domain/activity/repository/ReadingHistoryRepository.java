package com.youthletter.domain.activity.repository;

import com.youthletter.domain.activity.entity.ReadingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory> findByUserIdOrderByReadAtDesc(Long userId);

    long countByUserId(Long userId);

    @Query(value = "SELECT DISTINCT read_at::DATE FROM reading_history WHERE user_id = :userId ORDER BY read_at::DATE DESC",
           nativeQuery = true)
    List<LocalDate> findDistinctReadDatesByUserId(@Param("userId") Long userId);
}
