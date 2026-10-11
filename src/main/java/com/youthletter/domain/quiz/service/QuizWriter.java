package com.youthletter.domain.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.news.client.dto.AiQuizResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 기사당 퀴즈 세트는 한 번만 저장한다. 이미 있으면(풀이 기록이 참조할 수 있음) 덮어쓰지 않는다. */
@Component
@RequiredArgsConstructor
public class QuizWriter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean saveIfAbsent(Long articleId, AiQuizResponse response) {
        jdbc.queryForList("SELECT id FROM news_articles WHERE id=? FOR UPDATE", Long.class, articleId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM quizzes WHERE article_id=?)", Boolean.class, articleId)))
            return false;
        int order = 1;
        for (AiQuizResponse.Item q : response.quizzes()) {
            jdbc.update("""
                    INSERT INTO quizzes(article_id, quiz_order, question, choices, answer_index, explanation,
                                        source_sentence_ids, model_version, prompt_version)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?)
                    """, articleId, order++, q.question().strip(), json(q.choices().stream().map(String::strip).toList()),
                    q.answerIndex(), q.explanation().strip(), json(q.sourceSentenceIds()),
                    response.modelVersion(), response.promptVersion());
        }
        return true;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
