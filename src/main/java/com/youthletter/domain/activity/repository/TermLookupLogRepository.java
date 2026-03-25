package com.youthletter.domain.activity.repository;

import com.youthletter.domain.activity.entity.TermLookupLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermLookupLogRepository extends JpaRepository<TermLookupLog, Long> {

    List<TermLookupLog> findByUserIdOrderByLookedUpAtDesc(Long userId);
}
