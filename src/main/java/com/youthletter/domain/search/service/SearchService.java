package com.youthletter.domain.search.service;

import com.youthletter.domain.news.category.NewsCategory;
import com.youthletter.domain.search.dto.CategoryResponse;
import com.youthletter.domain.search.dto.KeywordCardResponse;
import com.youthletter.domain.search.dto.SearchSuggestionResponse;
import com.youthletter.domain.search.dto.TrendingKeywordResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 카테고리·키워드 카드·실시간 트렌드·자동완성.
 * 집계 기준
 * - 카테고리 isNew: 최근 24시간 내 발행(없으면 수집) 기사 존재
 * - 키워드 카드: 최근 7일 기사에 하이라이트된 금융 용어를 기사 수 순으로(이미지는 그중 최신 기사 썸네일)
 * - 트렌드: 최근 24시간 검색어를 '검색한 사용자 수'로 순위(한 사람이 반복 검색해도 1),
 *   직전 24시간 순위와 비교해 up/down/same
 * - 자동완성: 용어명 앞부분 일치 → 부분 일치 → 트렌드 검색어 앞부분 일치, 중복 제거
 */
@Service
@RequiredArgsConstructor
public class SearchService {
    public static final int MIN_KEYWORD = 2, MAX_KEYWORD = 50;
    private final JdbcTemplate jdbc;

    /** 공백 정리·소문자. 2~50자가 아니면 null(기록하지 않음). */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String k = raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        return k.length() < MIN_KEYWORD || k.length() > MAX_KEYWORD ? null : k;
    }

    @Transactional
    public void record(Long userId, String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (userId == null || keyword == null) return;
        jdbc.update("INSERT INTO search_logs(user_id, keyword) VALUES (?, ?)", userId, keyword);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        Set<String> fresh = new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT category FROM news_articles
                WHERE coalesce(published_at, crawled_at) >= now() - interval '24 hours'
                """, String.class));
        return Arrays.stream(NewsCategory.values())
                .map(c -> new CategoryResponse(c.id(), c.displayName(), fresh.contains(c.displayName())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KeywordCardResponse> keywordCards(int limit) {
        return jdbc.query("""
                SELECT t.id, t.name, count(DISTINCT a.id) AS cnt,
                       mode() WITHIN GROUP (ORDER BY a.category) AS category,
                       (array_agg(a.thumbnail_url ORDER BY coalesce(a.published_at, a.crawled_at) DESC)
                           FILTER (WHERE a.thumbnail_url IS NOT NULL))[1] AS image
                FROM article_term_highlights h
                JOIN news_articles a ON a.id = h.article_id
                JOIN financial_terms t ON t.id = h.term_id
                WHERE coalesce(a.published_at, a.crawled_at) >= now() - interval '7 days'
                GROUP BY t.id, t.name
                ORDER BY cnt DESC, max(coalesce(a.published_at, a.crawled_at)) DESC, t.id
                LIMIT ?
                """, (rs, i) -> {
            String categoryName = rs.getString("category");
            String categoryId = NewsCategory.resolve(categoryName).map(NewsCategory::id).orElse(null);
            return new KeywordCardResponse(rs.getLong("id"), rs.getString("name"), categoryId, categoryName,
                    rs.getLong("cnt"), rs.getString("image"));
        }, clamp(limit, 1, 20));
    }

    @Transactional(readOnly = true)
    public List<TrendingKeywordResponse> trending(int limit) {
        return jdbc.query("""
                WITH cur AS (
                    SELECT keyword, row_number() OVER (ORDER BY count(DISTINCT user_id) DESC, max(searched_at) DESC, keyword) AS r
                    FROM search_logs WHERE searched_at >= now() - interval '24 hours' GROUP BY keyword
                ), prev AS (
                    SELECT keyword, row_number() OVER (ORDER BY count(DISTINCT user_id) DESC, max(searched_at) DESC, keyword) AS r
                    FROM search_logs
                    WHERE searched_at >= now() - interval '48 hours' AND searched_at < now() - interval '24 hours'
                    GROUP BY keyword
                )
                SELECT cur.keyword, cur.r, prev.r AS prev_r FROM cur LEFT JOIN prev USING (keyword)
                ORDER BY cur.r LIMIT ?
                """, (rs, i) -> {
            int rank = rs.getInt("r");
            int prev = rs.getInt("prev_r");
            boolean isNew = rs.wasNull();
            String change = isNew || prev > rank ? "up" : prev < rank ? "down" : "same";
            return new TrendingKeywordResponse(rank, rs.getString("keyword"), change, isNew);
        }, clamp(limit, 1, 20));
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> suggestions(String rawQuery, int limit) {
        String q = rawQuery == null ? "" : rawQuery.strip().replaceAll("\\s+", " ");
        if (q.isEmpty() || q.length() > MAX_KEYWORD) return List.of();
        int max = clamp(limit, 1, 10);
        String escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        LinkedHashMap<String, SearchSuggestionResponse> out = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT name FROM financial_terms
                WHERE name ILIKE ? ESCAPE '\\' OR name ILIKE ? ESCAPE '\\'
                ORDER BY (name ILIKE ? ESCAPE '\\') DESC, length(name), name LIMIT ?
                """, String.class, escaped + "%", "%" + escaped + "%", escaped + "%", max)
                .forEach(name -> out.putIfAbsent(name.toLowerCase(Locale.ROOT), new SearchSuggestionResponse(name, "term")));
        if (out.size() < max) {
            jdbc.queryForList("""
                    SELECT keyword FROM search_logs
                    WHERE searched_at >= now() - interval '7 days' AND keyword LIKE ? ESCAPE '\\'
                    GROUP BY keyword ORDER BY count(DISTINCT user_id) DESC, keyword LIMIT ?
                    """, String.class, escaped.toLowerCase(Locale.ROOT) + "%", max)
                    .forEach(k -> out.putIfAbsent(k, new SearchSuggestionResponse(k, "trending")));
        }
        return out.values().stream().limit(max).toList();
    }

    /** 검색어 기록은 30일만 보관한다. */
    @Scheduled(cron = "${search-log.cleanup-cron:0 30 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldLogs() {
        jdbc.update("DELETE FROM search_logs WHERE searched_at < now() - interval '30 days'");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
