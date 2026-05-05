package com.youthletter.domain.spending.repository;

import com.youthletter.domain.spending.entity.SpendingInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpendingInsightRepository extends JpaRepository<SpendingInsight, Long> {

    Optional<SpendingInsight> findTopByUserIdOrderByCreatedAtDesc(Long userId);
}
