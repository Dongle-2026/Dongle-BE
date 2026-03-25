package com.youthletter.domain.term.controller;

import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Terms", description = "금융 용어 사전 API")
@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController {

    private final FinancialTermRepository termRepository;

    @Operation(summary = "용어 상세 조회")
    @GetMapping("/{termId}")
    public GlobalResponse<FinancialTerm> getTerm(@PathVariable Long termId) {
        FinancialTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));
        return GlobalResponse.ok(term);
    }

    @Operation(summary = "용어 검색 (전문 검색)", description = "tsvector 기반 한국어 검색")
    @GetMapping("/search")
    public GlobalResponse<List<FinancialTerm>> searchTerms(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        return GlobalResponse.ok(termRepository.searchByText(query, limit));
    }

    @Operation(summary = "용어 자동완성", description = "pg_trgm 기반 유사 문자열 매칭")
    @GetMapping("/autocomplete")
    public GlobalResponse<List<FinancialTerm>> autocomplete(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int limit) {
        return GlobalResponse.ok(termRepository.findBySimilarName(query, limit));
    }

    @Operation(summary = "카테고리별 용어 조회", description = "ltree 경로 기반 하위 용어 전체 조회")
    @GetMapping("/category")
    public GlobalResponse<List<FinancialTerm>> getByCategory(
            @RequestParam String path) {
        return GlobalResponse.ok(termRepository.findByPathDescendants(path));
    }

    @Operation(summary = "난이도별 용어 조회")
    @GetMapping("/level")
    public GlobalResponse<List<FinancialTerm>> getByLevel(
            @RequestParam(defaultValue = "3") Integer maxLevel) {
        return GlobalResponse.ok(
                termRepository.findByDifficultyLevelLessThanEqualOrderByNameAsc(maxLevel));
    }
}
