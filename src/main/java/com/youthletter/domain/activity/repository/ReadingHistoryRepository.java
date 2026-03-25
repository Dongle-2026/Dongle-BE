package com.youthletter.domain.activity.repository;

import com.youthletter.domain.activity.entity.ReadingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory> findByUserIdOrderByReadAtDesc(Long userId);
}
