package com.youthletter;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiQuizResponse;
import com.youthletter.domain.news.service.AiJobWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 본문 조회 → QUIZ 작업 접수 → 작업자 실행 → 검증 → 저장 → READY. AI HTTP 호출만 대체한다. */
@SpringBootTest(properties = {"crawler.cron=-", "ai-jobs.enabled=true", "ai-jobs.poll-ms=86400000"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class QuizGenerationPipelineTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiJobWorker worker;
    @MockitoBean AiServiceClient ai;
    Long articleId;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        when(ai.isStubMode()).thenReturn(false);
        articleId = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'t','제목','첫 문장이다. 둘째 문장이다. 셋째 문장이다.','금융',now()) RETURNING id",
                Long.class, "p-" + UUID.randomUUID());
    }
    static AiQuizResponse.Item item(String q) {
        return new AiQuizResponse.Item(q, List.of("가", "나", "다", "라"), 1, "해설", List.of(1));
    }
    int jobs() { return jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE article_id=? AND kind='QUIZ'", Integer.class, articleId); }
    int quizzes() { return jdbc.queryForObject("SELECT count(*) FROM quizzes WHERE article_id=?", Integer.class, articleId); }

    @Test void firstViewQueuesOneJobAndWorkerPublishesQuizzes() throws Exception {
        when(ai.generateQuiz(any())).thenReturn(new AiQuizResponse("m", "quiz-v1", List.of(item("첫째 문제"), item("둘째 문제"), item("셋째 문제"))));
        for (int i = 0; i < 2; i++)
            mvc.perform(get("/api/news/" + articleId + "/body")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quizStatus").value("GENERATING"));
        assertThat(jobs()).isEqualTo(1); // 반복 조회해도 작업 1건

        worker.tick();
        assertThat(quizzes()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_jobs WHERE article_id=? AND kind='QUIZ'", String.class, articleId)).isEqualTo("SUCCEEDED");
        mvc.perform(get("/api/news/" + articleId + "/body")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizStatus").value("READY"))
                .andExpect(jsonPath("$.data.quizzes.length()").value(3))
                .andExpect(jsonPath("$.data.quizzes[0].question").value("첫째 문제"));
        verify(ai, times(1)).generateQuiz(any());
        // 보기를 섞어도 정답 보기 텍스트는 유지된다(item()의 정답은 "나")
        for (var row : jdbc.queryForList("SELECT choices::text AS c, answer_index AS a FROM quizzes WHERE article_id=?", articleId)) {
            java.util.List<String> choices = new com.fasterxml.jackson.databind.ObjectMapper().readValue((String) row.get("c"), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            assertThat(choices).containsExactlyInAnyOrder("가", "나", "다", "라");
            assertThat(choices.get(((Number) row.get("a")).intValue())).isEqualTo("나");
        }
    }

    @Test void invalidModelOutputIsNotSaved() throws Exception {
        when(ai.generateQuiz(any())).thenReturn(new AiQuizResponse("m", "quiz-v1", List.of(item("a"), item("b")))); // 2문제
        mvc.perform(get("/api/news/" + articleId + "/body")).andExpect(jsonPath("$.data.quizStatus").value("GENERATING"));
        worker.tick();
        assertThat(quizzes()).isZero();
        assertThat(jdbc.queryForObject("SELECT error_code FROM ai_jobs WHERE article_id=? AND kind='QUIZ'", String.class, articleId))
                .isEqualTo("AI_SERVICE_ERROR");

        // 근거 문장 번호가 본문 범위를 벗어난 경우도 저장하지 않는다
        jdbc.update("UPDATE ai_jobs SET available_at = now() - interval '1 minute' WHERE article_id=?", articleId);
        when(ai.generateQuiz(any())).thenReturn(new AiQuizResponse("m", "quiz-v1", List.of(item("a"), item("b"),
                new AiQuizResponse.Item("c", List.of("가", "나", "다", "라"), 1, "해설", List.of(99)))));
        worker.tick();
        assertThat(quizzes()).isZero();
    }

    @Test void stubModeNeverQueues() throws Exception {
        when(ai.isStubMode()).thenReturn(true);
        mvc.perform(get("/api/news/" + articleId + "/body")).andExpect(jsonPath("$.data.quizStatus").value("UNAVAILABLE"));
        assertThat(jobs()).isZero();
    }
}
