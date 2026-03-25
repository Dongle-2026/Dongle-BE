package com.youthletter.domain.spending.repository;

import com.youthletter.domain.spending.entity.SpendingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpendingSnapshotRepository extends JpaRepository<SpendingSnapshot, Long> {

    Optional<SpendingSnapshot> findByUserIdAndSnapshotMonth(Long userId, String snapshotMonth);

    List<SpendingSnapshot> findByUserIdOrderBySnapshotMonthDesc(Long userId);
}
