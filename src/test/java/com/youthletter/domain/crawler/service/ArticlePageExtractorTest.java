package com.youthletter.domain.crawler.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticlePageExtractorTest {
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

    @Test void titleUsesHeadlineOnlyNotSectionLogo() {
        var doc = Jsoup.parse("<h1 class='logo-area'>경제</h1><h1 class='headline'> 청년도약계좌 확대 </h1>");
        assertThat(ArticlePageExtractor.title(doc)).isEqualTo("청년도약계좌 확대");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<html><head><meta property='og:title' content='OG 제목'></head><h1 class='logo-area'>경제</h1></html>")))
                .isEqualTo("OG 제목");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<h1>단일 제목</h1>"))).isEqualTo("단일 제목");
        assertThat(ArticlePageExtractor.title(Jsoup.parse("<p>없음</p>"))).isEmpty();
    }
}
