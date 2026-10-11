package com.youthletter.domain.crawler.service;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleImageExtractorTest {
    String extract(String head) { return ArticleImageExtractor.extract(Jsoup.parse("<html><head>" + head + "</head></html>", "https://www.hankyung.com/article/1")); }

    @Test void prefersOgImageThenTwitter() {
        assertThat(extract("<meta property='og:image' content='https://img.hankyung.com/a.jpg'><meta name='twitter:image' content='https://x/b.jpg'>"))
                .isEqualTo("https://img.hankyung.com/a.jpg");
        assertThat(extract("<meta name='twitter:image' content='https://img.hankyung.com/b.jpg'>")).isEqualTo("https://img.hankyung.com/b.jpg");
    }
    @Test void resolvesRelativeAndRejectsUnsafe() {
        assertThat(extract("<meta property='og:image' content='/photo/c.jpg'>")).isEqualTo("https://www.hankyung.com/photo/c.jpg");
        assertThat(ArticleImageExtractor.valid("javascript:alert(1)")).isNull();
        assertThat(ArticleImageExtractor.valid("data:image/png;base64,AAAA")).isNull();
        assertThat(ArticleImageExtractor.valid("https://x/" + "a".repeat(1000))).isNull();
        assertThat(extract("")).isNull();
        assertThat(extract("<meta property='og:image' content=''>")).isNull();
    }
}
