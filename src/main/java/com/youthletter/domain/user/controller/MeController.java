package com.youthletter.domain.user.controller;

import com.youthletter.domain.activity.dto.ReadHistoryResponse;
import com.youthletter.domain.activity.service.ActivityService;
import com.youthletter.domain.news.dto.NewsBookmarkResponse;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.domain.notification.controller.DeviceTokenController;
import com.youthletter.domain.notification.service.DeviceTokenService;
import com.youthletter.domain.term.dto.SavedTermResponse;
import com.youthletter.domain.term.service.TermService;
import com.youthletter.domain.user.dto.LearningStatsResponse;
import com.youthletter.domain.user.dto.UserProfileResponse;
import com.youthletter.domain.user.service.AuthService;
import com.youthletter.domain.user.service.UserService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** Compatibility routes; mobile adapters map these DTOs into screen models. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {
    private final UserService userService;
    private final NewsService newsService;
    private final TermService termService;
    private final ActivityService activityService;
    private final AuthService authService;
    private final DeviceTokenService deviceTokenService;

    @GetMapping
    public GlobalResponse<UserProfileResponse> profile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(userService.getMyProfile(principal.getUserId()));
    }

    /** 회원 탈퇴. DELETE /api/auth/withdraw와 같은 처리(soft delete + 토큰 무효화 + 기기 삭제). */
    @DeleteMapping
    public GlobalResponse<Void> withdraw(@AuthenticationPrincipal CustomUserPrincipal principal,
                                         HttpServletRequest request) {
        authService.withdraw(principal.getUserId(), resolveToken(request));
        return GlobalResponse.ok("회원 탈퇴가 완료되었습니다", null);
    }

    @GetMapping("/stats")
    public GlobalResponse<LearningStatsResponse> stats(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(userService.getLearningStats(principal.getUserId()));
    }

    @GetMapping("/saved-news")
    public GlobalResponse<Page<NewsBookmarkResponse>> savedNews(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return GlobalResponse.ok(newsService.getMyBookmarks(principal.getUserId(), pageable));
    }

    @GetMapping("/saved-terms")
    public GlobalResponse<Page<SavedTermResponse>> savedTerms(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        return GlobalResponse.ok(termService.getSavedTerms(principal.getUserId(), pageable));
    }

    @GetMapping("/read-history")
    public GlobalResponse<Page<ReadHistoryResponse>> readHistory(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 10) Pageable pageable) {
        return GlobalResponse.ok(activityService.getReadHistory(principal.getUserId(), pageable));
    }

    /** 푸시 토큰 등록. PUT /api/users/me/devices와 같은 upsert(같은 deviceId면 토큰 교체). */
    @PostMapping("/devices")
    public GlobalResponse<Void> registerDevice(@AuthenticationPrincipal CustomUserPrincipal principal,
                                               @Valid @RequestBody DeviceTokenController.Request request) {
        deviceTokenService.register(principal.getUserId(), request.deviceId(), request.token(), request.platform());
        return GlobalResponse.ok();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
