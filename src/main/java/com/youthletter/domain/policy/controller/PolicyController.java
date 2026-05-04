package com.youthletter.domain.policy.controller;

import com.youthletter.domain.policy.dto.PolicyDetailResponse;
import com.youthletter.domain.policy.dto.PolicyListResponse;
import com.youthletter.domain.policy.service.PolicyService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Policy", description = "청년 정책 추천 API")
@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @Operation(summary = "정책 목록 조회", description = "활성 정책 목록. regionCode 파라미터로 지역 필터 (미입력 시 전체)")
    @GetMapping
    public GlobalResponse<Page<PolicyListResponse>> getPolicyList(
            @RequestParam(required = false) String regionCode,
            @PageableDefault(size = 10) Pageable pageable) {
        return GlobalResponse.ok(policyService.getPolicyList(regionCode, pageable));
    }

    @Operation(summary = "맞춤 정책 추천", description = "사용자 프로필(나이, 지역, 소득) 기반으로 매칭된 정책 목록 반환")
    @GetMapping("/recommend")
    public GlobalResponse<Page<PolicyListResponse>> getRecommendedPolicies(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        return GlobalResponse.ok(policyService.getRecommendedPolicies(principal.getUserId(), pageable));
    }

    @Operation(summary = "정책 상세 조회")
    @GetMapping("/{policyId}")
    public GlobalResponse<PolicyDetailResponse> getPolicyDetail(
            @PathVariable Long policyId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        return GlobalResponse.ok(policyService.getPolicyDetail(policyId, userId));
    }

    @Operation(summary = "정책 즐겨찾기 토글", description = "이미 추가되어 있으면 해제, 없으면 추가")
    @PostMapping("/{policyId}/bookmark")
    public GlobalResponse<Boolean> toggleBookmark(
            @PathVariable Long policyId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        boolean bookmarked = policyService.toggleBookmark(principal.getUserId(), policyId);
        String message = bookmarked ? "즐겨찾기에 추가했습니다" : "즐겨찾기를 해제했습니다";
        return GlobalResponse.ok(message, bookmarked);
    }
}
