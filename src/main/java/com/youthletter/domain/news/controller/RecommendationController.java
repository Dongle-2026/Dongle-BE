package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.service.RecommendationService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RecommendationController {
    private final RecommendationService service;
    @GetMapping("/api/news/recommend")
    public GlobalResponse<List<RecommendationService.Recommendation>> recommend(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(service.recommend(principal.getUserId()));
    }
}
