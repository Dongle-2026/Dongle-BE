package com.youthletter.domain.report.controller;

import com.youthletter.domain.report.dto.*;
import com.youthletter.domain.report.dto.AdminContentReportResponse.ReportStatus;
import com.youthletter.domain.report.service.ContentReportService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "ContentReports", description = "퀴즈·카드 오류 신고와 관리자 처리")
public class ContentReportController {
    private final ContentReportService reports;
    public record StatusRequest(@NotNull ReportStatus status) {}

    @PostMapping("/api/quizzes/{quizId}/reports")
    @Operation(summary = "퀴즈 오류 신고", description = "로그인 필요. 같은 문제 재신고는 최초 신고 반환. 자동 숨김 없음")
    public GlobalResponse<ContentReportResponse> reportQuiz(@AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long quizId, @Valid @RequestBody ContentReportRequest request) {
        return GlobalResponse.ok(reports.reportQuiz(principal.getUserId(), quizId, request));
    }

    @PostMapping("/api/card-news/{cardNewsId}/reports")
    @Operation(summary = "카드뉴스 오류 신고", description = "기사 ID가 아닌 카드뉴스 묶음 ID 사용")
    public GlobalResponse<ContentReportResponse> reportCard(@AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long cardNewsId, @Valid @RequestBody ContentReportRequest request) {
        return GlobalResponse.ok(reports.reportCard(principal.getUserId(), cardNewsId, request));
    }

    @GetMapping("/api/me/content-reports")
    @Operation(summary = "내 신고 처리 현황", description = "본인 신고만 최신순 반환. status 선택, page=0, size=20")
    public GlobalResponse<Page<MyContentReportResponse>> mine(@AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) ReportStatus status, @PageableDefault(size = 20) Pageable pageable) {
        return GlobalResponse.ok(reports.listMine(principal.getUserId(), status, pageable));
    }

    @GetMapping("/api/admin/content-reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "신고 목록", description = "status 선택. 최신순 고정, page=0, size=20")
    public GlobalResponse<Page<AdminContentReportResponse>> list(@RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return GlobalResponse.ok(reports.list(status, pageable));
    }

    @PatchMapping("/api/admin/content-reports/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "신고 처리 상태 변경", description = "OPEN/REVIEWED/DISMISSED. 원본 콘텐츠나 보상은 변경하지 않음")
    public GlobalResponse<AdminContentReportResponse> update(@PathVariable Long reportId, @Valid @RequestBody StatusRequest request) {
        return GlobalResponse.ok(reports.updateStatus(reportId, request.status()));
    }
}
