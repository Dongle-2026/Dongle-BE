package com.youthletter.domain.user.dto;

public record LearningStatsResponse(
        long readNewsCount,
        long learnedTermCount,
        int consecutiveLearningDays
) {
}
