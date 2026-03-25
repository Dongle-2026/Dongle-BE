package com.youthletter.domain.policy.dto;

import com.youthletter.domain.policy.entity.Policy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Schema(description = "정책 목록 응답")
@Builder
public record PolicyListResponse(
        @Schema(description = "정책 ID") Long id,
        @Schema(description = "정책명") String title,
        @Schema(description = "AI 요약") String summary,
        @Schema(description = "기관명") String organization,
        @Schema(description = "마감일") LocalDate endDate,
        @Schema(description = "대상 나이 (최소)") Integer ageMin,
        @Schema(description = "대상 나이 (최대)") Integer ageMax
) {
    public static PolicyListResponse from(Policy policy) {
        return PolicyListResponse.builder()
                .id(policy.getId())
                .title(policy.getTitle())
                .summary(policy.getSummary())
                .organization(policy.getOrganization())
                .endDate(policy.getEndDate())
                .ageMin(policy.getAgeMin())
                .ageMax(policy.getAgeMax())
                .build();
    }
}
