package com.youthletter.domain.crawler.service;

import org.jsoup.Jsoup;
import org.jsoup.HttpStatusException;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaekyungCrawlerServiceTest {
    /** 수집 흐름 테스트용 실제 기사 길이 본문(짧은 본문은 건너뛰므로). */
    static final String BODY = "금리가 올랐다. ".repeat(60);
    static final String OFF_TOPIC = "판다가 대나무를 먹었다. ".repeat(40);

    String rss = """
        <rss><channel><item><link>https://www.mk.co.kr/news/economy/123?ref=rss</link>
        <pubDate>Sun, 18 Oct 2026 12:00:00 +09:00</pubDate></item>
        <item><link>https://www.mk.co.kr/news/economy/123</link></item>
        <item><link>https://evil.test/news/economy/124</link></item></channel></rss>
        """;

    @Test void normalizesDeduplicatesAndConvertsDateToUtc() {
        var entries = MaekyungCrawlerService.parseFeed(Jsoup.parse(rss, "", Parser.xmlParser()), "경제");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).url()).isEqualTo("https://www.mk.co.kr/news/economy/123");
        assertThat(entries.get(0).publishedAt()).isEqualTo("2026-10-18T03:00:00");
    }
    @Test void rejectsUntrustedUrlsAndChallengePages() {
        for (String url : new String[]{"http://localhost/news/a/123", "https://www.mk.co.kr.evil.test/news/a/123",
                "https://user@www.mk.co.kr/news/a/123", "https://www.mk.co.kr:444/news/a/123", "file:///news/a/123"}) {
            assertThat(MaekyungCrawlerService.canonicalUrl(url)).isNull();
        }
        assertThatThrownBy(() -> MaekyungCrawlerService.parseFeed(Jsoup.parse("<html>Just a moment</html>"), "경제"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void preservesSourceAndRequiresFullArticleBody() {
        var entry = new MaekyungCrawlerService.Entry("https://www.mk.co.kr/news/stock/123", "투자", null);
        var doc = Jsoup.parse("<meta property='og:title' content='실제 기사 제목'><div class='news_cnt_detail_wrap'>원문 본문<script>광고</script></div>");
        var article = MaekyungCrawlerService.parseArticle(doc, entry);
        assertThat(article.sourceName()).isEqualTo("매일경제");
        assertThat(article.category()).isEqualTo("투자");
        assertThat(article.content()).isEqualTo("원문 본문");
        assertThatThrownBy(() -> MaekyungCrawlerService.parseArticle(Jsoup.parse("<h1>제목만</h1>"), entry))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void accessDeniedStopsRequestsAndReportsFailure() throws Exception {
        var crawler = spy(new MaekyungCrawlerService());
        doThrow(new HttpStatusException("Forbidden", 403, "https://www.mk.co.kr/rss/30100041/"))
                .when(crawler).fetch(anyString(), anyBoolean());
        assertThatThrownBy(() -> crawler.crawl(url -> false)).isInstanceOf(IllegalStateException.class);
        verify(crawler, times(1)).fetch(anyString(), anyBoolean());
    }
    @Test void savedArticlesAreNotFetchedAndCrossSectionDuplicatesAreRemoved() throws Exception {
        var crawler = spy(new MaekyungCrawlerService());
        doReturn(Jsoup.parse(rss, "", Parser.xmlParser())).when(crawler).fetch(anyString(), eq(true));
        assertThat(crawler.crawl(url -> true)).isEmpty();
        verify(crawler, never()).fetch(anyString(), eq(false));
        doReturn(Jsoup.parse("<h1>제목</h1><div id='article_body'>" + BODY + "</div>"))
                .when(crawler).fetch(anyString(), eq(false));
        assertThat(crawler.crawl(url -> false)).hasSize(1);
        verify(crawler, times(1)).fetch(anyString(), eq(false));
    }
    @Test void liveMarkupExcludesImageControlsAndUsesArticleHeadline() {
        var entry = new MaekyungCrawlerService.Entry("https://www.mk.co.kr/news/economy/123", "경제", null);
        var doc = Jsoup.parse("<meta property='og:title' content='제목 - 매일경제'><h2 class='view_head_title'>제목</h2>"
                + "<meta property='article:published_time' content='2026-10-18T18:25:35+09:00'>"
                + "<div class='news_cnt_detail_wrap'><div class='mid_title'>부제목</div>"
                + "<div class='thumb_area'>사진 설명<button>사진 확대</button></div><p>첫 문장.</p><p>둘째 문장.</p></div>");
        var article = MaekyungCrawlerService.parseArticle(doc, entry);
        assertThat(article.title()).isEqualTo("제목");
        assertThat(article.content()).isEqualTo("첫 문장. 둘째 문장.");
        assertThat(article.publishedAt()).isEqualTo("2026-10-18T09:25:35");
    }
    @Test void emptyBodyDoesNotDiscardHealthyArticle() throws Exception {
        var crawler = spy(new MaekyungCrawlerService());
        var feed = Jsoup.parse("<rss><channel><item><link>https://www.mk.co.kr/news/economy/123</link></item>"
                + "<item><link>https://www.mk.co.kr/news/economy/124</link></item></channel></rss>", "", Parser.xmlParser());
        doReturn(feed).when(crawler).fetch(anyString(), eq(true));
        doReturn(Jsoup.parse("<h1>제목</h1><div id='article_body'></div>"))
                .when(crawler).fetch(eq("https://www.mk.co.kr/news/economy/123"), eq(false));
        doReturn(Jsoup.parse("<h1>제목</h1><div id='article_body'>" + BODY + "</div>"))
                .when(crawler).fetch(eq("https://www.mk.co.kr/news/economy/124"), eq(false));
        var result = crawler.crawlResult(url -> false);
        assertThat(result.articles()).hasSize(1);
        assertThat(result.failedCount()).isEqualTo(1);
    }

    @Test void shortPreviewBodyIsSkippedWithoutCountingAsFailure() throws Exception {
        var crawler = spy(new MaekyungCrawlerService());
        var feed = Jsoup.parse("<rss><channel><item><link>https://www.mk.co.kr/news/stock/200</link></item></channel></rss>", "", Parser.xmlParser());
        doReturn(feed).when(crawler).fetch(anyString(), eq(true));
        doReturn(Jsoup.parse("<h1>제목</h1><div id='article_body'><figure><figcaption>사진=매경DB</figcaption></figure>유료 기사 미리보기</div>"))
                .when(crawler).fetch(eq("https://www.mk.co.kr/news/stock/200"), eq(false));
        var result = crawler.crawlResult(url -> false);
        assertThat(result.articles()).isEmpty();
        assertThat(result.failedCount()).isZero();
    }

    @Test void photoCaptionsAreRemovedAndCategoryUsesTheSharedClassifier() {
        var entry = new MaekyungCrawlerService.Entry("https://www.mk.co.kr/news/realestate/300", "부동산", null);
        var doc = Jsoup.parse("<h1>전세대출 금리 인하</h1><div id='article_body'><figure><img src='a.jpg'><figcaption>사진=연합뉴스</figcaption></figure>"
                + BODY + "</div>");
        var article = MaekyungCrawlerService.parseArticle(doc, entry);
        assertThat(article.content()).doesNotContain("사진=").startsWith("금리가 올랐다.");
        assertThat(article.sourceName()).isEqualTo("매일경제");
        assertThat(article.category()).isEqualTo(com.youthletter.domain.news.category.NewsCategoryClassifier
                .classify(article.title(), article.content()).displayName());
    }

    @Test void offTopicLifestyleArticleIsSkippedWithoutCountingAsFailure() throws Exception {
        var crawler = spy(new MaekyungCrawlerService());
        var feed = Jsoup.parse("<rss><channel><item><link>https://www.mk.co.kr/news/culture/400</link></item>"
                + "<item><link>https://www.mk.co.kr/news/economy/401</link></item></channel></rss>", "", Parser.xmlParser());
        doReturn(feed).when(crawler).fetch(anyString(), eq(true));
        doReturn(Jsoup.parse("<h1>판다 생일</h1><div id='article_body'>" + OFF_TOPIC + "</div>"))
                .when(crawler).fetch(eq("https://www.mk.co.kr/news/culture/400"), eq(false));
        doReturn(Jsoup.parse("<h1>금리 인상</h1><div id='article_body'>" + BODY + "</div>"))
                .when(crawler).fetch(eq("https://www.mk.co.kr/news/economy/401"), eq(false));
        var result = crawler.crawlResult(url -> false);
        assertThat(result.articles()).extracting(a -> a.url()).containsExactly("https://www.mk.co.kr/news/economy/401");
        assertThat(result.failedCount()).isZero();
    }
}
