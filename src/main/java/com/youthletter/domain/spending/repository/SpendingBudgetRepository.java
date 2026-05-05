package com.youthletter.domain.spending.repository;

import com.youthletter.domain.spending.entity.SpendingBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpendingBudgetRepository extends JpaRepository<SpendingBudget, Long> {

    Optional<SpendingBudget> findByUserId(Long userId);
}
