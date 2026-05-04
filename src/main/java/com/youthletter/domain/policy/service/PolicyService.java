package com.youthletter.domain.policy.service;

import com.youthletter.domain.policy.dto.PolicyDetailResponse;
import com.youthletter.domain.policy.dto.PolicyListResponse;
import com.youthletter.domain.policy.entity.Policy;
import com.youthletter.domain.policy.entity.PolicyBookmark;
import com.youthletter.domain.policy.repository.PolicyBookmarkRepository;
import com.youthletter.domain.policy.repository.PolicyQueryRepository;
import com.youthletter.domain.policy.repository.PolicyRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final PolicyQueryRepository policyQueryRepository;
    private final PolicyBookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;

    /**
     * 정책 목록 조회 (지역 필터 + 활성 정책)
     */
    public Page<PolicyListResponse> getPolicyList(String regionCode, Pageable pageable) {
        return policyQueryRepository.findActivePolicies(regionCode, pageable)
                .map(PolicyListResponse::from);
    }

    /**
     * 프로필 기반 정책 추천 (QueryDSL 동적 쿼리)
     */
    public Page<PolicyListResponse> getRecommendedPolicies(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int age = user.getBirthDate() != null
                ? Period.between(user.getBirthDate(), LocalDate.now()).getYears()
                : 25;

        return policyQueryRepository.findByProfile(
                age, user.getRegionCode(), user.getIncomeLevel(), pageable)
                .map(PolicyListResponse::from);
    }

    /**
     * 정책 상세 조회
     */
    public PolicyDetailResponse getPolicyDetail(Long policyId, Long userId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        boolean isBookmarked = userId != null
                && bookmarkRepository.existsByUserIdAndPolicyId(userId, policyId);

        return PolicyDetailResponse.from(policy, isBookmarked);
    }

    /**
     * 정책 즐겨찾기 토글
     */
    @Transactional
    public boolean toggleBookmark(Long userId, Long policyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        return bookmarkRepository.findByUserIdAndPolicyId(userId, policyId)
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return false;  // 즐겨찾기 해제
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(PolicyBookmark.builder()
                            .user(user)
                            .policy(policy)
                            .build());
                    return true;  // 즐겨찾기 추가
                });
    }
}
