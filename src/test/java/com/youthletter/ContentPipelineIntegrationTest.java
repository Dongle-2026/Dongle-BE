package com.youthletter;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.*;
import com.youthletter.domain.news.service.*;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.global.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"crawler.cron=-", "ai-jobs.enabled=true", "ai-jobs.poll-ms=86400000"})
@ActiveProfiles("test")
@Transactional
class ContentPipelineIntegrationTest {
    @Autowired ContentPipelineStore store;
    @Autowired AiJobWorker worker;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiServiceClient ai;

    CrawledArticleDto article() {
        return new CrawledArticleDto("https://example.invalid/" + UUID.randomUUID(), "제목",
                "첫 문장이다. 둘째 문장이다. 셋째 문장이다.", "금융", null, null);
    }
    int count(String table, Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE article_id=?", Integer.class, id);
    }
    @Test void savesRawAndAllResultsWithoutAnyPageViewAndDoesNotDuplicateOnRestart() {
        when(ai.isStubMode()).thenReturn(false);
        when(ai.process(any())).thenReturn(new AiProcessResponse("첫 요약. 둘째 요약. 셋째 요약.", List.of(), null));
        when(ai.generateCards(any())).thenReturn(new AiCardResponse("test-model", "cards-v1", IntStream.rangeClosed(1,5)
                .mapToObj(i -> new AiCardResponse.Card(i, CardTemplateType.T02, "제목", "내용", null, null, List.of(1))).toList()));
        when(ai.generateQuiz(any())).thenReturn(new AiQuizResponse("test-model", "quiz-v1", List.of("첫 문제", "둘째 문제", "셋째 문제")
                .stream().map(q -> new AiQuizResponse.Item(q,List.of("가","나","다","라"),0,"해설",List.of(1))).toList()));
        var source = article();
        Long id = store.save(source);
        assertThat(store.save(source)).isEqualTo(id);
        assertThat(count("ai_jobs", id)).isEqualTo(3);
        worker.tick(); worker.tick(); worker.tick();
        assertThat(count("news_summaries", id)).isEqualTo(1);
        assertThat(count("card_news", id)).isEqualTo(1);
        assertThat(count("quizzes", id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE article_id=? AND status='SUCCEEDED'",Integer.class,id)).isEqualTo(3);
        store.save(source);
        assertThat(count("ai_jobs", id)).isEqualTo(3);
    }
    @Test void aiFailureKeepsRawArticleAndBoundedRetryInDatabase() {
        when(ai.isStubMode()).thenReturn(false);
        when(ai.process(any())).thenThrow(new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT));
        var source=article();
        Long id=store.save(source);
        worker.tick();
        assertThat(jdbc.queryForObject("SELECT content FROM news_articles WHERE id=?",String.class,id)).isEqualTo(source.content());
        assertThat(jdbc.queryForObject("SELECT status FROM ai_jobs WHERE article_id=? AND kind='ARTICLE'",String.class,id)).isEqualTo("QUEUED");
        jdbc.update("UPDATE ai_jobs SET attempts=2,available_at=now()-interval '1 second' WHERE article_id=? AND kind='ARTICLE'",id);
        worker.tick();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_jobs WHERE article_id=? AND kind='ARTICLE'",String.class,id)).isEqualTo("FAILED");
        store.save(source);
        assertThat(count("ai_jobs",id)).isEqualTo(3);
    }
}
