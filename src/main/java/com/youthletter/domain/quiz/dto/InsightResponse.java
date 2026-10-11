package com.youthletter.domain.quiz.dto;

import java.util.List;

/**
 * correctRate: 문제별 첫 시도 정답률(%), 푼 문제가 없으면 null.
 * interestedCategories: 최근 30일 많이 읽은 카테고리 최대 3개.
 * weakCategories: 3문제 이상 푼 카테고리 중 첫 시도 정답률 60% 미만, 낮은 순 최대 2개.
 */
public record InsightResponse(Integer correctRate, int totalQuizCount,
                              List<String> interestedCategories, List<String> weakCategories) {
}
