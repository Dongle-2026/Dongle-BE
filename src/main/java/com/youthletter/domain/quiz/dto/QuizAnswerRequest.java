package com.youthletter.domain.quiz.dto;

/** selectedIndex(0~3) 또는 choiceId(a~d) 중 하나 */
public record QuizAnswerRequest(Integer selectedIndex, String choiceId) {
}
