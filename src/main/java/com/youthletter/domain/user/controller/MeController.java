package com.youthletter.domain.user.controller;

import com.youthletter.domain.news.dto.NewsBookmarkResponse;
import com.youthletter.domain.news.service.NewsService;
import com.youthletter.domain.user.dto.LearningStatsResponse;
import com.youthletter.domain.user.dto.UserProfileResponse;
import com.youthletter.domain.user.service.UserService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compatibility routes; mobile adapters map these DTOs into screen models. */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {
    private final UserService userService;
    private final NewsService newsService;

    @GetMapping
    public GlobalResponse<UserProfileResponse> profile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(userService.getMyProfile(principal.getUserId()));
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
}
