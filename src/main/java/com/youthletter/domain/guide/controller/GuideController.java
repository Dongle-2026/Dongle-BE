package com.youthletter.domain.guide.controller;

import com.youthletter.domain.guide.entity.Guide;
import com.youthletter.domain.guide.repository.GuideRepository;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Guide", description = "주식/보험 가이드 API")
@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private final GuideRepository guideRepository;

    @Operation(summary = "가이드 목록 조회", description = "타입별 가이드 목록 (STOCK, INSURANCE)")
    @GetMapping
    public GlobalResponse<Page<Guide>> getGuides(
            @RequestParam String type,
            @PageableDefault(size = 10) Pageable pageable) {
        return GlobalResponse.ok(
                guideRepository.findByGuideTypeOrderByDifficultyLevelAsc(type, pageable));
    }

    @Operation(summary = "가이드 상세 조회")
    @GetMapping("/{guideId}")
    public GlobalResponse<Guide> getGuide(@PathVariable Long guideId) {
        return GlobalResponse.ok(
                guideRepository.findById(guideId).orElseThrow());
    }
}
