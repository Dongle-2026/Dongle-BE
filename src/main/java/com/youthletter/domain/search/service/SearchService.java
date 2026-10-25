package com.youthletter.domain.search.service;

import com.youthletter.domain.news.category.NewsCategory;
import com.youthletter.domain.search.dto.CategoryResponse;
import com.youthletter.domain.search.dto.KeywordCardResponse;
import com.youthletter.domain.search.dto.SearchSuggestionResponse;
import com.youthletter.domain.search.dto.TrendingKeywordResponse;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
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
 * - 키워드 카드: 기사에 하이라이트된 금융 용어를 최근 7일 기사 수 → 전체 기사 수 순으로(이미지는 그중 최신 기사 썸네일).
 *   category를 주면 그 카테고리 기사만. 모자라면 카테고리별 기본 키워드(KeywordSeeds, 10개)로 채운다.
 * - 트렌드: 최근 24시간 검색어를 '검색한 사용자 수'로 순위(한 사람이 반복 검색해도 1),
 *   직전 24시간 순위와 비교해 up/down/same. 모자라면 키워드 카드 순서로 채운다(change=same).
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
    public List<KeywordCardResponse> keywordCards(String categoryParam, int limit) {
        NewsCategory category = null;
        if (categoryParam != null && !categoryParam.isBlank())
            category = NewsCategory.resolve(categoryParam.strip())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 카테고리입니다"));
        int max = clamp(limit, 1, 20);
        String categoryName = category == null ? null : category.displayName();

        // 1) 기사에 나온 용어: 최근 7일 기사 수 → 전체 기사 수 → 최신 순. 7일 밖 용어로도 채운다.
        List<KeywordCardResponse> out = new ArrayList<>(jdbc.query("""
                SELECT t.id, t.name,
                       count(DISTINCT a.id) FILTER (WHERE coalesce(a.published_at, a.crawled_at) >= now() - interval '7 days') AS recent_cnt,
                       mode() WITHIN GROUP (ORDER BY a.category) AS category,
                       (array_agg(a.thumbnail_url ORDER BY coalesce(a.published_at, a.crawled_at) DESC)
                           FILTER (WHERE a.thumbnail_url IS NOT NULL))[1] AS image
                FROM article_term_highlights h
                JOIN news_articles a ON a.id = h.article_id
                JOIN financial_terms t ON t.id = h.term_id
                WHERE (CAST(? AS text) IS NULL OR a.category = ?)
                GROUP BY t.id, t.name
                ORDER BY recent_cnt DESC, count(DISTINCT a.id) DESC, max(coalesce(a.published_at, a.crawled_at)) DESC, t.id
                LIMIT ?
                """, (rs, i) -> {
            String name = rs.getString("category");
            return new KeywordCardResponse(rs.getLong("id"), rs.getString("name"),
                    NewsCategory.resolve(name).map(NewsCategory::id).orElse(null), name,
                    rs.getLong("recent_cnt"), rs.getString("image"));
        }, categoryName, categoryName, max));

        // 2) 그래도 모자라면 시연용 기본 키워드
        if (out.size() < max) {
            Set<String> used = new HashSet<>();
            out.forEach(c -> used.add(c.keyword().toLowerCase(Locale.ROOT)));
            List<KeywordSeeds.Seed> seeds = category == null ? KeywordSeeds.interleaved()
                    : KeywordSeeds.of(category).stream().map(k -> new KeywordSeeds.Seed(k, null)).toList();
            List<KeywordSeeds.Seed> missing = new ArrayList<>();
            for (KeywordSeeds.Seed seed : seeds)
                if (missing.size() < max - out.size() && used.add(seed.keyword().toLowerCase(Locale.ROOT))) missing.add(seed);
            for (KeywordSeeds.Seed seed : missing) {
                NewsCategory c = category != null ? category : seed.category();
                out.add(seedCard(seed.keyword(), c));
            }
        }
        return out;
    }

    /** 기본 키워드 카드. 같은 이름의 용어가 있으면 그 id, 이미지는 제목에 키워드가 든 최신 기사(없으면 같은 카테고리 최신 기사). */
    private KeywordCardResponse seedCard(String keyword, NewsCategory category) {
        Long termId = jdbc.query("SELECT id FROM financial_terms WHERE lower(name) = lower(?)",
                (rs, i) -> rs.getLong("id"), keyword).stream().findFirst().orElse(null);
        String pattern = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String image = jdbc.query("""
                SELECT thumbnail_url FROM news_articles
                WHERE thumbnail_url IS NOT NULL AND (title ILIKE ? OR category = ?)
                ORDER BY (title ILIKE ?) DESC, coalesce(published_at, crawled_at) DESC
                LIMIT 1
                """, (rs, i) -> rs.getString(1), pattern, category.displayName(), pattern).stream().findFirst().orElse(null);
        return new KeywordCardResponse(termId, keyword, category.id(), category.displayName(), 0, image);
    }

    @Transactional(readOnly = true)
    public List<TrendingKeywordResponse> trending(int limit) {
        int max = clamp(limit, 1, 20);
        List<TrendingKeywordResponse> out = new ArrayList<>(searchedTrending(max));
        if (out.size() < max) {
            // 검색 기록이 모자라면 기사에 많이 나온 용어(키워드 카드 순서)로 채운다. 순위 변동 정보가 없으므로 same.
            Set<String> used = new HashSet<>();
            out.forEach(t -> used.add(t.keyword().toLowerCase(Locale.ROOT)));
            for (KeywordCardResponse card : keywordCards(null, 20)) {
                if (out.size() >= max) break;
                if (used.add(card.keyword().toLowerCase(Locale.ROOT)))
                    out.add(new TrendingKeywordResponse(out.size() + 1, card.keyword(), "same", false));
            }
        }
        return out;
    }

    private List<TrendingKeywordResponse> searchedTrending(int max) {
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
        }, max);
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
