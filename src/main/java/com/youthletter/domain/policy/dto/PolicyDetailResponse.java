package com.youthletter.domain.policy.dto;

import com.youthletter.domain.policy.entity.Policy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Schema(description = "정책 상세 응답")
@Builder
public record PolicyDetailResponse(
        @Schema(description = "정책 ID") Long id,
        @Schema(description = "정책명") String title,
        @Schema(description = "상세 설명") String description,
        @Schema(description = "AI 요약") String summary,
        @Schema(description = "기관명") String organization,
        @Schema(description = "지역 코드") String regionCode,
        @Schema(description = "대상 나이 (최소)") Integer ageMin,
        @Schema(description = "대상 나이 (최대)") Integer ageMax,
        @Schema(description = "소득 기준") String incomeCriteria,
        @Schema(description = "시작일") LocalDate startDate,
        @Schema(description = "마감일") LocalDate endDate,
        @Schema(description = "신청 URL") String applicationUrl,
        @Schema(description = "즐겨찾기 여부") boolean isBookmarked
) {
    public static PolicyDetailResponse from(Policy policy, boolean isBookmarked) {
        return PolicyDetailResponse.builder()
                .id(policy.getId())
                .title(policy.getTitle())
                .description(policy.getDescription())
                .summary(policy.getSummary())
                .organization(policy.getOrganization())
                .regionCode(policy.getRegionCode())
                .ageMin(policy.getAgeMin())
                .ageMax(policy.getAgeMax())
                .incomeCriteria(policy.getIncomeCriteria())
                .startDate(policy.getStartDate())
                .endDate(policy.getEndDate())
                .applicationUrl(policy.getApplicationUrl())
                .isBookmarked(isBookmarked)
                .build();
    }
}
