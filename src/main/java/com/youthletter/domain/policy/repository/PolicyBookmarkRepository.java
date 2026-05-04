package com.youthletter.domain.policy.repository;

import com.youthletter.domain.policy.entity.PolicyBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyBookmarkRepository extends JpaRepository<PolicyBookmark, Long> {

    Page<PolicyBookmark> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<PolicyBookmark> findByUserIdAndPolicyId(Long userId, Long policyId);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);

    java.util.List<PolicyBookmark> findByPolicyId(Long policyId);
}
