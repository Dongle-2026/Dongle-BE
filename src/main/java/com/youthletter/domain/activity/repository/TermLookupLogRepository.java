package com.youthletter.domain.activity.repository;

import com.youthletter.domain.activity.entity.TermLookupLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TermLookupLogRepository extends JpaRepository<TermLookupLog, Long> {

    List<TermLookupLog> findByUserIdOrderByLookedUpAtDesc(Long userId);

    @Query("SELECT COUNT(DISTINCT t.term.id) FROM TermLookupLog t WHERE t.user.id = :userId")
    long countDistinctTermByUserId(@Param("userId") Long userId);

    @Query("SELECT t FROM TermLookupLog t JOIN FETCH t.term WHERE t.user.id = :userId ORDER BY t.lookedUpAt DESC")
    List<com.youthletter.domain.activity.entity.TermLookupLog> findByUserIdWithTermOrderByDesc(@Param("userId") Long userId);
}
