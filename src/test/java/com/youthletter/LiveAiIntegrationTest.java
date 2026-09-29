package com.youthletter;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.service.NewsAiWriter;
import com.youthletter.domain.cardnews.service.*;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 명시적으로 실행한 경우에만 실제 로컬 모델을 호출한다. 테스트 DB 데이터는 rollback한다. */
@SpringBootTest(properties="crawler.cron=-")
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_AI_SMOKE", matches="true")
class LiveAiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired AiServiceClient client;
    @org.springframework.beans.factory.annotation.Autowired NewsAiWriter newsWriter;
    @org.springframework.beans.factory.annotation.Autowired CardNewsWriter cardWriter;
    @org.springframework.beans.factory.annotation.Autowired CardNewsValidator validator;
    @org.springframework.beans.factory.annotation.Autowired CardNewsRepository cards;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @Test void springNodeOllamaPostgresRoundTrip() {
        String content = "이 문서는 실제 뉴스가 아닌 연동 시험용 자료입니다. 예산은 일정 기간 사용할 금액을 계획하는 것입니다. 저축은 사용하지 않은 돈을 보관하는 행동입니다. 청년 금융 교육에서는 소비를 기록하고 정기적으로 확인하는 습관을 설명합니다. 참여자는 자신의 지출 계획을 점검할 수 있습니다.";
        String url = "https://example.invalid/" + UUID.randomUUID();
        Long id = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?, 'fixture','금융 교육 시험',?,'YOUTH') RETURNING id", Long.class, url, content);
        var request = new AiProcessRequest(url, "금융 교육 시험", content, "YOUTH");
        var processed = client.process(request);
        assertThat(processed.embedding()).hasSize(768);
        newsWriter.replace(id, processed, "qwen3:8b");
        em.flush();
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM news_articles WHERE id=?", Integer.class, id)).isEqualTo(768);
        var generated = client.generateCards(request);
        validator.validate(generated, content);
        cardWriter.save(id, generated);
        em.flush(); em.clear();
        assertThat(cards.findByArticleId(id).orElseThrow().getCards()).hasSizeBetween(5,7);
    }
}
