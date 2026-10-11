package com.youthletter.domain.quiz.controller;

import com.youthletter.domain.quiz.dto.*;
import com.youthletter.domain.quiz.service.LearningService;
import com.youthletter.domain.quiz.service.QuizService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Learning", description = "본문 학습·퀴즈·꿀")
@RestController
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;
    private final LearningService learningService;

    @Operation(summary = "뉴스 본문·용어·퀴즈", description = "로그인 시 내 퀴즈 결과·용어 저장 여부 포함. quizStatus=GENERATING이면 잠시 후 다시 조회")
    @GetMapping("/api/news/{articleId}/body")
    public GlobalResponse<NewsBodyResponse> body(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                 @PathVariable Long articleId) {
        return GlobalResponse.ok(quizService.body(articleId, principal == null ? null : principal.getUserId()));
    }

    @Operation(summary = "퀴즈 제출", description = "재도전 가능. 꿀은 문제당 첫 정답 10꿀. 오답이면 정답·해설 비공개")
    @PostMapping("/api/quizzes/{quizId}/answer")
    public GlobalResponse<QuizAnswerResponse> answer(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                     @PathVariable Long quizId, @RequestBody QuizAnswerRequest request) {
        return GlobalResponse.ok(quizService.answer(principal.getUserId(), quizId, request));
    }

    @Operation(summary = "꿀단지 히트맵", description = "최근 days일(기본 126, 최대 365), 한국 날짜")
    @GetMapping("/api/me/activity")
    public GlobalResponse<List<ActivityDayResponse>> activity(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                              @RequestParam(defaultValue = "126") int days) {
        return GlobalResponse.ok(learningService.activity(principal.getUserId(), days));
    }

    @Operation(summary = "관심/더 알아볼 분야")
    @GetMapping("/api/me/insights")
    public GlobalResponse<InsightResponse> insights(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(learningService.insights(principal.getUserId()));
    }
}
