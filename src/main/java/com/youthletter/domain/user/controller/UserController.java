package com.youthletter.domain.user.controller;

import com.youthletter.domain.news.dto.NewsBookmarkResponse;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.domain.policy.dto.PolicyListResponse;
import com.youthletter.domain.policy.service.PolicyService;
import com.youthletter.domain.term.dto.LearnedTermResponse;
import com.youthletter.domain.term.service.TermService;
import com.youthletter.domain.user.dto.*;
import com.youthletter.domain.user.service.UserService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NewsService newsService;
    private final TermService termService;
    private final PolicyService policyService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public ResponseEntity<GlobalResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(GlobalResponse.ok(userService.getMyProfile(principal.getUserId())));
    }

    @Operation(summary = "프로필 수정 (닉네임)")
    @PatchMapping("/me")
    public ResponseEntity<GlobalResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(GlobalResponse.ok(userService.updateProfile(principal.getUserId(), request)));
    }

    @Operation(summary = "관심 카테고리 설정", description = "경제/투자/보험/대출/정책/청년금융 중 다중 선택")
    @PutMapping("/me/categories")
    public ResponseEntity<GlobalResponse<Void>> updateCategories(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CategoryUpdateRequest request) {
        userService.updateCategories(principal.getUserId(), request);
        return ResponseEntity.ok(GlobalResponse.ok("관심 카테고리가 업데이트되었습니다", null));
    }

    @Operation(summary = "정책 추천용 프로필 설정", description = "나이, 거주 지역, 취업 여부, 소득 구간")
    @PutMapping("/me/policy-profile")
    public ResponseEntity<GlobalResponse<Void>> updatePolicyProfile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestBody PolicyProfileRequest request) {
        userService.updatePolicyProfile(principal.getUserId(), request);
        return ResponseEntity.ok(GlobalResponse.ok("정책 추천 프로필이 업데이트되었습니다", null));
    }

    @Operation(summary = "학습 진행 현황 조회", description = "읽은 뉴스 수, 학습한 용어 수, 연속 학습일")
    @GetMapping("/me/learning-stats")
    public ResponseEntity<GlobalResponse<LearningStatsResponse>> getLearningStats(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(GlobalResponse.ok(userService.getLearningStats(principal.getUserId())));
    }

    @Operation(summary = "내 뉴스 북마크 목록", description = "최신 북마크 순 정렬")
    @GetMapping("/me/bookmarks")
    public ResponseEntity<GlobalResponse<Page<NewsBookmarkResponse>>> getMyBookmarks(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(GlobalResponse.ok(newsService.getMyBookmarks(principal.getUserId(), pageable)));
    }

    @Operation(summary = "내가 학습한 용어 목록", description = "학습 날짜 최신순, 용어별 중복 제거")
    @GetMapping("/me/learned-terms")
    public ResponseEntity<GlobalResponse<List<LearnedTermResponse>>> getLearnedTerms(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(GlobalResponse.ok(termService.getLearnedTerms(principal.getUserId())));
    }

    @Operation(summary = "내 정책 북마크 목록", description = "최신 북마크 순 정렬")
    @GetMapping("/me/policy-bookmarks")
    public ResponseEntity<GlobalResponse<Page<PolicyListResponse>>> getMyPolicyBookmarks(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(GlobalResponse.ok(policyService.getMyPolicyBookmarks(principal.getUserId(), pageable)));
    }
}
