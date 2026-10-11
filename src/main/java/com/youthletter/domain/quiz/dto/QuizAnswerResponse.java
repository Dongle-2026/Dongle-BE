package com.youthletter.domain.quiz.dto;

/**
 * 재도전 가능하므로 오답이면 정답·해설을 공개하지 않는다(correctIndex/explanation=null).
 * earnedHoney: 이번 제출로 받은 꿀. 이미 맞힌 문제를 다시 맞히면 0, alreadySolved=true.
 * totalHoney: 제출 처리 후 내 누적 꿀(헤더 표시 갱신용).
 */
public record QuizAnswerResponse(Long quizId, int selectedIndex, boolean isCorrect, int earnedHoney,
                                 boolean alreadySolved, Integer correctIndex, String explanation,
                                 long totalHoney) {
}
