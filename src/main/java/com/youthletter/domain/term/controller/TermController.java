package com.youthletter.domain.term.controller;

import com.youthletter.domain.term.dto.TermDetailResponse;
import com.youthletter.domain.term.dto.TermListResponse;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import com.youthletter.domain.term.service.TermService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Terms", description = "금융 용어 사전 API")
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController {

    private final FinancialTermRepository termRepository;  // autocomplete, category, level 조회용
    private final TermService termService;

    @Operation(summary = "용어 전체 목록", description = "난이도순 정렬, 페이지네이션. path 파라미터로 ltree 카테고리 필터 가능")
    @GetMapping
    public ResponseEntity<GlobalResponse<Page<TermListResponse>>> getTermList(
            @RequestParam(required = false) String path,
            @PageableDefault(size = 20, sort = "difficultyLevel", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(GlobalResponse.ok(termService.getTermList(path, pageable)));
    }

    @Operation(summary = "용어 상세 조회", description = "관련 뉴스 목록 최대 5건 포함, 로그인 시 학습 여부 표시")
    @GetMapping("/{termId}")
    public ResponseEntity<GlobalResponse<TermDetailResponse>> getTerm(
            @PathVariable Long termId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        return ResponseEntity.ok(GlobalResponse.ok(termService.getTermDetail(termId, userId)));
    }

    @Operation(summary = "용어 학습 완료 처리", description = "학습 이력을 저장합니다")
    @PostMapping("/{termId}/learn")
    public ResponseEntity<GlobalResponse<Void>> learnTerm(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long termId) {
        termService.learnTerm(principal.getUserId(), termId);
        return ResponseEntity.ok(GlobalResponse.ok("용어 학습 완료", null));
    }

    @Operation(summary = "용어 저장", description = "반복 호출해도 저장 상태 유지. 학습 이력과 별개")
    @PostMapping("/{termId}/save")
    public GlobalResponse<Map<String, Boolean>> saveTerm(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long termId) {
        return GlobalResponse.ok(Map.of("bookmarked", termService.setTermBookmark(principal.getUserId(), termId, true)));
    }

    @Operation(summary = "용어 저장 해제", description = "반복 호출해도 해제 상태 유지. 학습 이력은 지우지 않음")
    @PostMapping("/{termId}/unsave")
    public GlobalResponse<Map<String, Boolean>> unsaveTerm(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long termId) {
        return GlobalResponse.ok(Map.of("bookmarked", termService.setTermBookmark(principal.getUserId(), termId, false)));
    }

    @Operation(summary = "용어 검색 (전문 검색)", description = "tsvector 기반")
    @GetMapping("/search")
    public ResponseEntity<GlobalResponse<List<TermListResponse>>> searchTerms(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(GlobalResponse.ok(termService.searchTerms(query, limit)));
    }

    @Operation(summary = "용어 자동완성", description = "pg_trgm 기반 유사 문자열")
    @GetMapping("/autocomplete")
    public ResponseEntity<GlobalResponse<List<FinancialTerm>>> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(GlobalResponse.ok(termRepository.findBySimilarName(query, limit)));
    }

    @Operation(summary = "카테고리별 용어 조회", description = "ltree 경로 기반 하위 용어")
    @GetMapping("/category")
    public ResponseEntity<GlobalResponse<List<FinancialTerm>>> getByCategory(
            @RequestParam String path) {
        return ResponseEntity.ok(GlobalResponse.ok(termRepository.findByPathDescendants(path)));
    }

    @Operation(summary = "난이도별 용어 조회")
    @GetMapping("/level")
    public ResponseEntity<GlobalResponse<List<FinancialTerm>>> getByLevel(
            @RequestParam(defaultValue = "3") Integer maxLevel) {
        return ResponseEntity.ok(GlobalResponse.ok(
                termRepository.findByDifficultyLevelLessThanEqualOrderByNameAsc(maxLevel)));
    }
}
