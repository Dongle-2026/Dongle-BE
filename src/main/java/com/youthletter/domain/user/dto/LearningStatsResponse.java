package com.youthletter.domain.user.dto;

public record LearningStatsResponse(
        long readNewsCount,
        long learnedTermCount,
        int consecutiveLearningDays,
        boolean todayCompleted,   // 오늘(한국 날짜) 기사 1개 이상 읽음
        long totalHoney,
        long totalQuizCount,      // 한 번 이상 제출한 문제 수
        long todayHoney,          // 오늘(한국 날짜) 받은 꿀
        long correctCount         // 한 번이라도 맞힌 문제 수
) {
}
