package com.youthletter.domain.crawler.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

/** RSS는 기사 발견에만 사용하며, 원문 본문을 읽지 못하면 저장하지 않는다. */
@Slf4j
@Service
@ConditionalOnProperty(name = "crawler.maekyung.enabled", havingValue = "true")
public class MaekyungCrawlerService implements NewsCrawler {
    @Value("${crawler.maekyung.max-per-section:10}")
    private int maxPerSection = 10;

    /** 매일경제는 기사 회전이 빨라 30분마다 확인한다. */
    @Value("${crawler.maekyung.interval-ms:1800000}")
    private long intervalMs = 1_800_000;

    @Override
    public java.time.Duration interval() {
        return java.time.Duration.ofMillis(intervalMs);
    }

    record Section(String feed, String category) {}
    record Entry(String url, String category, java.time.LocalDateTime publishedAt) {}
    private static final List<Section> SECTIONS = List.of(
            new Section("30100041", "경제"), new Section("50200011", "투자"),
            new Section("50300009", "부동산"), new Section("50100032", "경제"));

    @Override
    public List<CrawledArticleDto> crawl(Predicate<String> alreadySaved) {
        var result = crawlResult(alreadySaved);
        if (result.articles().isEmpty() && result.failedCount() > 0) {
            throw new IllegalStateException("매일경제 수집 실패");
        }
        return result.articles();
    }

    @Override
    public Result crawlResult(Predicate<String> alreadySaved) {
        var entries = new LinkedHashMap<String, Entry>();
        var articles = new ArrayList<CrawledArticleDto>();
        int failed = 0;
        try {
            for (var section : SECTIONS) {
                var feed = fetch("https://www.mk.co.kr/rss/" + section.feed() + "/", true);
                parseFeed(feed, section.category()).stream()
                        .filter(entry -> !alreadySaved.test(entry.url()) && !entries.containsKey(entry.url()))
                        .limit(Math.max(1, maxPerSection))
                        .forEach(entry -> entries.put(entry.url(), entry));
            }
            for (var entry : entries.values()) {
                Document doc = fetch(entry.url(), false);
                try {
                    var article = parseArticle(doc, entry);
                    // 한경과 같은 기준: 유료 미리보기처럼 짧은 본문은 요약·카드뉴스를 만들 수 없어 건너뛴다(실패 아님).
                    if (article.content().length() < HankyungCrawlerService.MIN_BODY_CHARS) {
                        log.info("[Crawler] 매일경제 본문이 짧아 건너뜀({}자): {}", article.content().length(), entry.url());
                        continue;
                    }
                    articles.add(article);
                } catch (IllegalStateException e) {
                    failed++;
                    log.warn("[Crawler] 매일경제 본문 파싱 제외: {} ({})", entry.url(), e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failed++;
        } catch (IOException | IllegalStateException e) {
            // 접근 차단 시 추가 요청을 멈추고 이미 읽은 기사는 보존한다.
            failed++;
            log.warn("[Crawler] 매일경제 수집 중단: {}", e.getMessage());
        }
        return new Result(List.copyOf(articles), failed);
    }

    Document fetch(String url, boolean xml) throws IOException, InterruptedException {
        Thread.sleep(1000);
        var response = Jsoup.connect(url).userAgent("Youthletter/1.0")
                .timeout(10000).maxBodySize(2_000_000).followRedirects(false)
                .parser(xml ? Parser.xmlParser() : Parser.htmlParser()).execute();
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return response.parse();
    }

    static List<Entry> parseFeed(Document feed, String category) {
        if (feed.selectFirst("rss > channel") == null) {
            throw new IllegalStateException("매일경제 RSS 형식이 아닙니다");
        }
        var entries = new LinkedHashMap<String, Entry>();
        for (var item : feed.select("item")) {
            String url = canonicalUrl(item.select("link").text());
            if (url == null) continue;
            java.time.LocalDateTime publishedAt = null;
            try {
                publishedAt = ZonedDateTime.parse(item.select("pubDate").text().replaceAll("([+-][0-9]{2}):([0-9]{2})$", "$1$2"), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (java.time.format.DateTimeParseException ignored) { }
            entries.putIfAbsent(url, new Entry(url, category, publishedAt));
        }
        return List.copyOf(entries.values());
    }

    static String canonicalUrl(String value) {
        try {
            var uri = URI.create(value.trim());
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || !("www.mk.co.kr".equalsIgnoreCase(uri.getHost()) || "mk.co.kr".equalsIgnoreCase(uri.getHost()))
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || !uri.getPath().matches("/news/[a-zA-Z-]+/[0-9]+/?")) return null;
            return "https://www.mk.co.kr" + uri.getPath().replaceAll("/$", "");
        } catch (IllegalArgumentException e) { return null; }
    }

    static CrawledArticleDto parseArticle(Document doc, Entry entry) {
        var headline = doc.selectFirst("h2.view_head_title");
        String title = headline != null ? headline.text().trim() : ArticlePageExtractor.title(doc);
        var body = doc.selectFirst(".news_cnt_detail_wrap, #article_body");
        if (body == null || title.isBlank()) throw new IllegalStateException("매일경제 본문/제목 없음");
        body = body.clone();
        body.select("figure, figcaption, script, style, iframe, .ad, .advertisement, .related_news, .thumb_area, .mid_title, button, .is_blind").remove();
        String content = body.text().trim();
        if (content.isBlank()) throw new IllegalStateException("매일경제 본문 비어 있음");
        var publishedAt = ArticlePageExtractor.publishedAt(doc, ZoneOffset.UTC);
        // 카테고리는 한경과 같은 분류기로 정한다(앱 카테고리 체계 통일). 키워드가 하나도 없으면 RSS 피드 카테고리를 쓴다.
        String category = com.youthletter.domain.news.category.NewsCategoryClassifier.match(title, content)
                .map(com.youthletter.domain.news.category.NewsCategory::displayName).orElse(entry.category());
        return new CrawledArticleDto(entry.url(), title, content, category,
                publishedAt != null ? publishedAt : entry.publishedAt(), ArticlePageExtractor.extract(doc), "매일경제");
    }
}
