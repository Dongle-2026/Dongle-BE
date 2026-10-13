package com.youthletter;

import com.youthletter.domain.cardnews.service.CardNewsWriter;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "crawler.cron=-")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class NewsFeedIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CardNewsWriter writer;
    @Autowired JwtTokenProvider tokens;

    @Test void returnsStoredSlidesPersonalSaveStateAndStablePagination() throws Exception {
        String marker = UUID.randomUUID().toString();
        Long user = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?, 'feed', ?) RETURNING id",
                Long.class, marker+"@example.invalid", marker.substring(0,20));
        Long article = insert(marker, "2026-01-02T00:00:00Z");
        insert(marker, "2026-01-01T00:00:00Z");
        writer.save(article, new AiCardResponse("test", "feed-test", java.util.stream.IntStream.rangeClosed(1,5)
                .mapToObj(i -> new AiCardResponse.Card(i, CardTemplateType.T01, "제목"+i, "본문"+i, null,null,List.of(1))).toList()));
        jdbc.update("INSERT INTO news_bookmarks(user_id,article_id) VALUES (?,?)", user, article);
        String access = tokens.createAccessToken(user, marker+"@example.invalid", "USER");
        mvc.perform(get("/api/news").param("page","1").param("limit","1").param("keyword",marker).param("category","finance")
                .header("Authorization","Bearer "+access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(article.toString()))
                .andExpect(jsonPath("$.data[0].category").value("finance"))
                .andExpect(jsonPath("$.data[0].isSaved").value(true))
                .andExpect(jsonPath("$.data[0].slides.length()").value(5))
                .andExpect(jsonPath("$.data[0].slides[0].type").value("image"))
                .andExpect(jsonPath("$.data[0].slides[0].description").value("본문1"))
                .andExpect(jsonPath("$.data[0].slides[1].type").value("content"))
                .andExpect(jsonPath("$.meta.hasNext").value(true));
        mvc.perform(get("/api/news").param("page","2").param("limit","1").param("keyword",marker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].slides").isEmpty())
                .andExpect(jsonPath("$.data[0].isSaved").value(false))
                .andExpect(jsonPath("$.meta.hasNext").value(false));
        mvc.perform(get("/api/news").param("limit","1").param("keyword",marker))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].isSaved").value(false));
        mvc.perform(get("/api/news").param("limit","1").param("keyword",marker).param("page","3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        for (String limit : List.of("0", "101")) mvc.perform(get("/api/news").param("limit",limit)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/news").param("limit","1").param("page","0")).andExpect(status().isBadRequest());
    }

    private Long insert(String marker, String date) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'test',?,'본문','금융',?::timestamptz) RETURNING id",
                Long.class, "https://example.invalid/"+UUID.randomUUID(), marker, date);
    }
}
