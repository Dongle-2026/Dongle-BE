package com.youthletter.domain.quiz.service;

import com.youthletter.domain.news.client.dto.AiQuizResponse;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

/** AI 서비스 검증과 별개로 저장 전 구조를 다시 확인한다(외부 응답을 신뢰하지 않음). */
@Component
public class QuizValidator {
    public void validate(AiQuizResponse response, String articleContent) {
        List<AiQuizResponse.Item> items = response == null ? null : response.quizzes();
        if (items == null || items.size() != 3) throw invalid();
        int sentenceCount = QuizText.sentences(articleContent).size();
        var questions = new HashSet<String>();
        for (AiQuizResponse.Item q : items) {
            if (!text(q.question(), 120) || !text(q.explanation(), 150) || q.choices() == null || q.choices().size() != 4
                    || q.choices().stream().anyMatch(c -> !text(c, 60))
                    || q.choices().stream().map(String::strip).distinct().count() != 4
                    || q.answerIndex() < 0 || q.answerIndex() > 3
                    || q.sourceSentenceIds() == null || q.sourceSentenceIds().isEmpty()
                    || q.sourceSentenceIds().stream().anyMatch(id -> id == null || id < 1 || id > sentenceCount)
                    || !questions.add(q.question().strip())) throw invalid();
        }
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI 퀴즈 형식 오류");
    }
}
