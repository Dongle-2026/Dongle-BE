package com.youthletter.domain.quiz.service;

import java.util.Arrays;
import java.util.List;

/** AI 서비스(ai.js sentences)와 같은 규칙으로 기사 본문을 문장으로 나눈다. */
final class QuizText {
    private QuizText() {}

    static List<String> sentences(String content) {
        if (content == null) return List.of();
        return Arrays.stream(content.split("(?<=[.!?。])\\s+|\\n+"))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
