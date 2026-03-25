package com.youthletter.domain.policy.repository;

import com.youthletter.domain.policy.entity.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    /**
     * 프로필 기반 정책 매칭
     * age_min/age_max 범위, region_code, end_date 필터
     */
    @Query("SELECT p FROM Policy p WHERE " +
           "(p.ageMin IS NULL OR p.ageMin <= :age) AND " +
           "(p.ageMax IS NULL OR p.ageMax >= :age) AND " +
           "(p.regionCode IS NULL OR p.regionCode = :regionCode) AND " +
           "(p.endDate IS NULL OR p.endDate >= :today) " +
           "ORDER BY p.endDate ASC NULLS LAST")
    Page<Policy> findMatchingPolicies(@Param("age") int age,
                                      @Param("regionCode") String regionCode,
                                      @Param("today") LocalDate today,
                                      Pageable pageable);

    /**
     * 마감 임박 정책 (알림 배치용)
     * end_date가 N일 이내인 정책 조회
     */
    @Query("SELECT p FROM Policy p WHERE p.endDate IS NOT NULL " +
           "AND p.endDate BETWEEN :today AND :deadline " +
           "ORDER BY p.endDate ASC")
    List<Policy> findPoliciesExpiringBetween(@Param("today") LocalDate today,
                                             @Param("deadline") LocalDate deadline);

    /**
     * pgvector 유사도 검색 (RAG Agent용)
     */
    @Query(value = "SELECT * FROM policies " +
                   "WHERE embedding IS NOT NULL " +
                   "ORDER BY embedding <=> CAST(:embedding AS vector) " +
                   "LIMIT :limit",
           nativeQuery = true)
    List<Policy> findByEmbeddingSimilarity(@Param("embedding") String embedding,
                                           @Param("limit") int limit);
}
