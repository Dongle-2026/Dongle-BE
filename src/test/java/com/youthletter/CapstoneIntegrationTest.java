package com.youthletter;

import com.youthletter.domain.news.repository.*;
import com.youthletter.domain.cardnews.service.CardNewsWriter;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.domain.spending.repository.SpendingEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = "crawler.cron=-")
@ActiveProfiles("test")
@Transactional
class CapstoneIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired NewsArticleRepository articles;
    @org.springframework.beans.factory.annotation.Autowired RecommendationRepository recommendations;
    @org.springframework.beans.factory.annotation.Autowired SpendingEventRepository events;
    @org.springframework.beans.factory.annotation.Autowired com.youthletter.domain.spending.service.SpendingService spending;
    @org.springframework.beans.factory.annotation.Autowired CardNewsWriter writer;
    @org.springframework.beans.factory.annotation.Autowired CardNewsRepository cards;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    Long user;
    @BeforeEach void setup() {
        String key = UUID.randomUUID().toString();
        user = jdbc.queryForObject("INSERT INTO users(email,name,nickname,interests) VALUES (?, 'test', ?, '[\"LOAN\"]') RETURNING id", Long.class, key + "@test.invalid", key.substring(0, 20));
    }
    Long article(String title, String category) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?, 'test', ?, '본문', ?, CURRENT_TIMESTAMP) RETURNING id", Long.class, UUID.randomUUID().toString(), title, category);
    }
    @Test void searchCombinesKeywordCategoryAndDate() {
        Long id = article("검색용 대출금리", "LOAN");
        article("검색용 정책", "POLICY");
        var result = articles.search("LOAN", "대출금리", LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay(), PageRequest.of(0,20));
        assertThat(result.getContent()).extracting(a -> a.getId()).containsExactly(id);
        assertThat(articles.search(null, null, null, null, PageRequest.of(0,20)).getTotalElements()).isGreaterThanOrEqualTo(2);
    }
    @Test void latestStateFiltersAfterDateChangesAndExcludesDeletion() {
        LocalDate today = LocalDate.now();
        for (String type : List.of("CREATED", "DELETED")) event(101, type, today, 100);
        event(102, "CREATED", today, 200);
        event(102, "UPDATED", today.minusMonths(1), 200);
        assertThat(events.findCurrentStateByUserAndPeriod(user, today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))).isEmpty();
        var trend = events.findMonthlyTrend(user);
        assertThat(trend).hasSize(6);
        assertThat(new java.math.BigDecimal(trend.get(5)[1].toString())).isZero();
        assertThat(new java.math.BigDecimal(trend.get(4)[1].toString())).isEqualByComparingTo("200");
    }
    void event(long id, String type, LocalDate date, int amount) {
        jdbc.update("INSERT INTO spending_events(user_id,event_type,spending_id,amount,category,spent_at) VALUES (?, ?, ?, ?, 'FOOD', ?)", user, type, id, amount, date);
    }
    @Test void spendingUsesDatabaseIdsAndDeletionRemainsFinal() {
        var request = new com.youthletter.domain.spending.dto.SpendingCreateRequest(java.math.BigDecimal.TEN,
                "FOOD", null, LocalDate.now(), null);
        Long first = spending.createSpending(user, request);
        Long second = spending.createSpending(user, request);
        assertThat(second).isGreaterThan(first);
        spending.deleteSpending(user, first);
        spending.deleteSpending(user, first);
        assertThat(events.findBySpendingIdOrderByCreatedAtAsc(first)).hasSize(2);
        assertThatThrownBy(() -> spending.updateSpending(user, first, request))
                .isInstanceOf(com.youthletter.global.exception.BusinessException.class);
    }
    @Test void recommendationExcludesReadAndWorksWithoutVectors() {
        Long read = article("읽은 기사", "LOAN");
        Long unread = article("새 기사", "LOAN");
        jdbc.update("INSERT INTO reading_history(user_id,article_id) VALUES (?, ?)", user, read);
        var result = recommendations.recommend(user);
        assertThat(result).extracting(RecommendationRepository.Candidate::getId).contains(unread).doesNotContain(read);
        assertThat(result.stream().filter(c -> c.getId().equals(unread)).findFirst().orElseThrow().getReasonCode()).isEqualTo("INTEREST_CATEGORY");
    }
    @Test void cardReplacementPreservesIdAndPersistsEvidence() {
        Long id = article("카드 시험", "LOAN");
        var generated = new AiCardResponse("test-model", "v1", java.util.stream.IntStream.rangeClosed(1,5)
                .mapToObj(i -> new AiCardResponse.Card(i, CardTemplateType.T01, "제목", "본문", null,null,List.of(1))).toList());
        writer.save(id, generated);
        em.flush(); em.clear();
        Long cardId = cards.findByArticleId(id).orElseThrow().getId();
        writer.save(id, generated);
        em.flush(); em.clear();
        var saved = cards.findByArticleId(id).orElseThrow();
        assertThat(saved.getId()).isEqualTo(cardId);
        assertThat(saved.getCards()).hasSize(5);
        assertThat(saved.getCards().get(0).getSourceSentenceIds()).containsExactly(1);
    }

    @Test void stubCannotOverwriteRealCards() {
        Long id = article("실제 생성물 보호", "LOAN");
        var content = List.of(new AiCardResponse.Card(1, CardTemplateType.T01,
                "기존 제목", "기존 본문", null, null, List.of(1)));
        writer.save(id, new AiCardResponse("real-model", "v1", content));
        assertThatThrownBy(() -> writer.save(id, new AiCardResponse("stub", "stub", content)))
                .isInstanceOf(com.youthletter.global.exception.BusinessException.class);
        em.clear();
        var saved = cards.findByArticleId(id).orElseThrow();
        assertThat(saved.getModelVersion()).isEqualTo("real-model");
        assertThat(saved.getCards().get(0).getTitle()).isEqualTo("기존 제목");
    }

    @Test void recommendationUsesStoredEmbedding() {
        Long read = article("벡터 기준", "YOUTH");
        Long unread = article("벡터 후보", "YOUTH");
        String vector = java.util.Collections.nCopies(768, "0.1").toString();
        jdbc.update("UPDATE news_articles SET embedding=CAST(? AS vector) WHERE id IN (?, ?)", vector, read, unread);
        jdbc.update("INSERT INTO reading_history(user_id,article_id) VALUES (?, ?)", user, read);
        var result = recommendations.recommend(user).stream().filter(c -> c.getId().equals(unread)).findFirst().orElseThrow();
        assertThat(result.getReasonCode()).isEqualTo("SIMILAR_TO_ACTIVITY");
        assertThat(result.getScore()).isGreaterThan(0.59);
    }
}
