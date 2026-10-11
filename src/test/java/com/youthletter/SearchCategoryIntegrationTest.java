package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.news.category.NewsCategory;
import com.youthletter.domain.news.category.NewsCategoryClassifier;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A20 카테고리, A21 키워드 카드, A23 트렌드, A24 자동완성 + 기사 자동 분류. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class SearchCategoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em;
    String m;
    List<Long> users = new ArrayList<>();

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        m = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 4; i++)
            users.add(jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id", Long.class,
                    "s" + i + m + "@test.invalid", "s" + i + m));
    }
    String bearer(int i) { return "Bearer " + tokens.createAccessToken(users.get(i), "s" + i + m + "@test.invalid", "USER"); }
    Long article(String title, String category, String publishedAgo) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at,crawled_at) " +
                "VALUES (?,'t',?,'본문',?,now() - ?::interval, now() - ?::interval) RETURNING id", Long.class,
                "u-" + UUID.randomUUID(), title, category, publishedAgo, publishedAgo);
    }
    Long term(String name) {
        return jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?,'t','d','s') RETURNING id", Long.class, name);
    }
    void highlight(Long articleId, Long termId) {
        jdbc.update("INSERT INTO article_term_highlights(article_id,term_id,position_start,position_end) VALUES (?,?,0,1)", articleId, termId);
    }
    void searched(int user, String keyword, String ago) {
        jdbc.update("INSERT INTO search_logs(user_id,keyword,searched_at) VALUES (?,?,now() - ?::interval)", users.get(user), keyword, ago);
    }
    JsonNode data(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
    }
    JsonNode find(JsonNode list, String field, String value) {
        for (JsonNode n : list) if (n.get(field).asText().equals(value)) return n;
        return null;
    }

    @Test void classifierUsesTitleWeightedKeywords() {
        assertThat(NewsCategoryClassifier.classify("청년도약계좌 가입자 100만 돌파", "정부 지원 적금")).isEqualTo(NewsCategory.YOUTH);
        assertThat(NewsCategoryClassifier.classify("코스피 2600 회복, ETF 자금 유입", "")).isEqualTo(NewsCategory.INVESTMENT);
        assertThat(NewsCategoryClassifier.classify("서울 아파트 전세가 상승", "주택 시장")).isEqualTo(NewsCategory.REAL_ESTATE);
        assertThat(NewsCategoryClassifier.classify("하반기 채용 늘린다", "일자리")).isEqualTo(NewsCategory.JOB);
        assertThat(NewsCategoryClassifier.classify("한국은행 기준금리 동결", "물가")).isEqualTo(NewsCategory.ECONOMY);
        assertThat(NewsCategoryClassifier.classify("무제", "내용 없음")).isEqualTo(NewsCategory.FINANCE); // 기본값
    }

    @Test void categoriesListAllEightWithFreshnessAndFilterById() throws Exception {
        Long fresh = article("청년 소식 " + m, "청년", "1 hour");
        article("오래된 부동산 " + m, "부동산", "3 days");
        JsonNode list = data("/api/categories");
        assertThat(list.size()).isEqualTo(8);
        assertThat(list.get(0).get("id").asText()).isEqualTo("youth");
        assertThat(find(list, "id", "youth").get("isNew").asBoolean()).isTrue();
        assertThat(find(list, "id", "real-estate").get("name").asText()).isEqualTo("부동산");

        JsonNode page = data("/api/news?category=youth&keyword=" + m);
        assertThat(page.get("content").size()).isEqualTo(1);
        assertThat(page.at("/content/0/id").asLong()).isEqualTo(fresh);
        assertThat(data("/api/news?category=청년&keyword=" + m).get("content").size()).isEqualTo(1);
    }

    @Test void adminReclassifiesExistingArticles() throws Exception {
        Long a = article("청년 적금 가입 급증 청년 " + m, "금융", "1 hour");
        mvc.perform(post("/api/admin/news/reclassify").header("Authorization", bearer(0))).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", users.get(1)); // 권한은 토큰이 아니라 DB 역할로 판정
        String admin = bearer(1);
        mvc.perform(post("/api/admin/news/reclassify").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updated").isNumber());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT category FROM news_articles WHERE id=?", String.class, a)).isEqualTo("청년");
    }

    @Test void keywordCardsRankRecentTermsByArticleCount() throws Exception {
        Long hot = term("핫" + m), warm = term("웜" + m), stale = term("옛" + m);
        Long a1 = article("a1", "투자", "1 hour"), a2 = article("a2", "투자", "2 days"), old = article("old", "경제", "10 days");
        highlight(a1, hot); highlight(a1, hot); highlight(a2, hot); highlight(a2, warm); highlight(old, stale);
        JsonNode cards = data("/api/keywords?limit=20");
        JsonNode h = find(cards, "keyword", "핫" + m), w = find(cards, "keyword", "웜" + m);
        assertThat(h.get("articleCount").asLong()).isEqualTo(2); // 같은 기사 중복 하이라이트는 1
        assertThat(h.get("category").asText()).isEqualTo("investment");
        assertThat(h.get("id").asLong()).isEqualTo(hot);
        assertThat(w.get("articleCount").asLong()).isEqualTo(1);
        assertThat(find(cards, "keyword", "옛" + m)).isNull(); // 7일 지난 기사만 있는 용어
        int hi = -1, wi = -1;
        for (int i = 0; i < cards.size(); i++) { if (cards.get(i) == h) hi = i; if (cards.get(i) == w) wi = i; }
        assertThat(hi).isLessThan(wi);
    }

    @Test void trendingCountsDistinctUsersAndComparesWithPreviousDay() throws Exception {
        String a = "aa" + m, b = "bb" + m, c = "cc" + m;
        for (int u = 0; u < 3; u++) searched(u, a, "1 hour");
        for (int u = 0; u < 2; u++) searched(u, b, "2 hours");
        for (int i = 0; i < 5; i++) searched(3, c, "3 hours"); // 한 사람의 반복 검색은 1
        for (int u = 0; u < 4; u++) searched(u, b, "30 hours");
        for (int u = 0; u < 2; u++) searched(u, a, "30 hours");
        JsonNode list = data("/api/search/trending?limit=20");
        JsonNode ta = find(list, "keyword", a), tb = find(list, "keyword", b), tc = find(list, "keyword", c);
        assertThat(ta.get("rank").asInt()).isLessThan(tb.get("rank").asInt());
        assertThat(tb.get("rank").asInt()).isLessThan(tc.get("rank").asInt());
        assertThat(ta.get("change").asText()).isEqualTo("up");
        assertThat(tb.get("change").asText()).isEqualTo("down");
        assertThat(tc.get("change").asText()).isEqualTo("up");
        assertThat(tc.get("isNew").asBoolean()).isTrue();
        assertThat(ta.get("isNew").asBoolean()).isFalse();
    }

    @Test void searchesAreRecordedOnlyForSignedInFirstPage() throws Exception {
        String k = "기록" + m;
        mvc.perform(get("/api/news/search").param("q", "  " + k.toUpperCase() + "  ").header("Authorization", bearer(0))).andExpect(status().isOk());
        mvc.perform(get("/api/news").param("keyword", k).header("Authorization", bearer(1))).andExpect(status().isOk());
        mvc.perform(get("/api/news/search").param("q", k).param("page", "1").header("Authorization", bearer(2))).andExpect(status().isOk());
        mvc.perform(get("/api/news/search").param("q", k)).andExpect(status().isOk());
        mvc.perform(get("/api/news/search").param("q", "가").header("Authorization", bearer(3))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_logs WHERE keyword=?", Integer.class, k.toLowerCase())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM search_logs WHERE user_id=?", Integer.class, users.get(3))).isZero();
    }

    @Test void suggestionsPreferTermPrefixAndEscapeWildcards() throws Exception {
        term(m + "금리"); term("기준" + m + "금리");
        searched(0, m + "검색", "1 hour");
        JsonNode list = data("/api/search/suggestions?q=" + m);
        assertThat(list.get(0).get("keyword").asText()).isEqualTo(m + "금리");
        assertThat(list.get(0).get("type").asText()).isEqualTo("term");
        assertThat(find(list, "keyword", "기준" + m + "금리")).isNotNull();
        assertThat(find(list, "keyword", m + "검색").get("type").asText()).isEqualTo("trending");
        assertThat(data("/api/search/suggestions?q=%25").size()).isZero(); // '%'는 문자로 취급
        assertThat(data("/api/search/suggestions?q=%20").size()).isZero();
        mvc.perform(get("/api/search/suggestions")).andExpect(status().isBadRequest());
    }
}
