package com.youthletter.domain.quiz.dto;

/** 정답 보기 결과. 이 문제는 이후 맞혀도 꿀을 받지 않는다. */
public record QuizRevealResponse(Long quizId, int correctIndex, String correctChoiceId, String explanation) {
}
