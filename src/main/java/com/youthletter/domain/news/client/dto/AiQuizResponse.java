package com.youthletter.domain.news.client.dto;

import java.util.List;

public record AiQuizResponse(String modelVersion, String promptVersion, List<Item> quizzes) {
    public record Item(String question, List<String> choices, int answerIndex, String explanation,
                       List<Integer> sourceSentenceIds) {}
}
