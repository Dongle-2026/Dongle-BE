package com.youthletter.domain.crawler.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticlePageExtractorTest {
    @Test void publicationTimePreservesInstantInStorageTimezone() {
        var doc = Jsoup.parse("<meta property='article:published_time' content='2026-10-11T20:40:03+09:00'>"
                + "<meta property='article:modified_time' content='2026-10-13T10:00:00+09:00'>");
        assertThat(ArticlePageExtractor.publishedAt(doc, java.time.ZoneId.of("UTC")))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 11, 11, 40, 3));
        assertThat(ArticlePageExtractor.publishedAt(doc, java.time.ZoneId.of("Asia/Seoul")))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 11, 20, 40, 3));
    }

    @Test void malformedPublicationFallsBackToExplicitPublicationElement() {
        var doc = Jsoup.parse("<meta property='article:published_time' content='invalid'>"
                + "<time itemprop='datePublished' datetime='2026-10-10T23:30:00Z'></time>");
        assertThat(ArticlePageExtractor.publishedAt(doc, java.time.ZoneId.of("Asia/Seoul")))
                .isEqualTo(java.time.LocalDateTime.of(2026, 10, 11, 8, 30));
    }

    @Test void unknownPublicationDoesNotInventTimeOrUseModificationDate() {
        for (String head : new String[]{"", "<meta property='article:modified_time' content='2026-10-13T10:00:00Z'>",
                "<meta property='article:published_time' content='2026-10-11'>",
                "<meta property='article:published_time' content='2026-10-11T20:40:03'>"}) {
            assertThat(ArticlePageExtractor.publishedAt(Jsoup.parse(head), java.time.ZoneId.of("UTC"))).isNull();
        }
    }

    String extract(String head) { return ArticlePageExtractor.extract(Jsoup.parse("<html><head>" + head + "</head></html>", "https://www.hankyung.com/article/1")); }

    @Test void prefersOgImageThenTwitter() {
        assertThat(extract("<meta property='og:image' content='https://img.hankyung.com/a.jpg'><meta name='twitter:image' content='https://x/b.jpg'>"))
                .isEqualTo("https://img.hankyung.com/a.jpg");
        assertThat(extract("<meta name='twitter:image' content='https://img.hankyung.com/b.jpg'>")).isEqualTo("https://img.hankyung.com/b.jpg");
    }
    @Test void resolvesRelativeAndRejectsUnsafe() {
        assertThat(extract("<meta property='og:image' content='/photo/c.jpg'>")).isEqualTo("https://www.hankyung.com/photo/c.jpg");
        assertThat(ArticlePageExtractor.valid("javascript:alert(1)")).isNull();
        assertThat(ArticlePageExtractor.valid("data:image/png;base64,AAAA")).isNull();
        assertThat(ArticlePageExtractor.valid("https://x/" + "a".repeat(1000))).isNull();
        assertThat(extract("")).isNull();
        assertThat(extract("<meta property='og:image' content=''>")).isNull();
    }

    @Test void rejectsSiteLogoAndDefaultImages() {
        assertThat(extract("<meta property='og:image' content='https://static.hankyung.com/img/logo/logo-news-sns.png?v=20260101'>"
                + "<meta name='twitter:image' content='https://img.hankyung.com/photo/202610/AA.1.jpg'>"))
                .isEqualTo("https://img.hankyung.com/photo/202610/AA.1.jpg");
        assertThat(extract("<meta property='og:image' content='https://static.hankyung.com/img/logo/logo-news-sns.png'>")).isNull();
        for (String url : new String[]{"https://x.com/images/logo.png", "https://x.com/logo_og.jpg", "https://x.com/img/noimage.gif",
                "https://x.com/img/default-og.png", "https://x.com/static/logos/brand.svg"}) {
            assertThat(ArticlePageExtractor.isPlaceholder(url)).as(url).isTrue();
        }
        for (String url : new String[]{"https://img.hankyung.com/photo/202610/AD.41496436.1.jpg",
                "https://x.com/photo/catalogue.jpg", "https://x.com/news/logistics-port.jpg", "https://x.com/a.jpg?ref=logo"}) {
            assertThat(ArticlePageExtractor.isPlaceholder(url)).as(url).isFalse();
        }
    }

    @Test void titleUsesHeadlineOnlyNotSectionLogo() {
        var doc = Jsoup.parse("<h1 class='logo-area'>경제</h1><h1 class='headline'> 청년도약계좌 확대 </h1>");
        assertThat(ArticlePageExtractor.title(doc)).isEqualTo("청년도약계좌 확대");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<html><head><meta property='og:title' content='OG 제목'></head><h1 class='logo-area'>경제</h1></html>")))
                .isEqualTo("OG 제목");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<h1>단일 제목</h1>"))).isEqualTo("단일 제목");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<p>없음</p>"))).isEmpty();
    }
}
