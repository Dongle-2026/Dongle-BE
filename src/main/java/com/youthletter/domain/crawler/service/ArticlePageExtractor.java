package com.youthletter.domain.crawler.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** 기사 원문 페이지에서 대표 이미지(og:image → twitter:image, http(s)만)와 제목을 뽑는다. */
public final class ArticlePageExtractor {
    static final int MAX_LENGTH = 1000;
    // 기사 사진이 없을 때 사이트가 넣는 로고·기본 이미지(예: 한경 /img/logo/logo-news-sns.png)는 썸네일로 쓰지 않는다.
    private static final Pattern PLACEHOLDER_IMAGE = Pattern.compile(
            "(?i)(^|/)(logo|logos)(/|[-_.])|/[^/]*(no[-_]?image|default[-_]?(img|image|thumb|og))[^/]*$");

    private ArticlePageExtractor() {}

    /** 발행 시각만 사용한다. 수정일이나 수집일로 대체하지 않는다.
     * 기존 JDBC LocalDateTime 저장 규칙에 맞춰 명시한 서버 시간대로 변환한다.
     */
    public static LocalDateTime publishedAt(Document doc, ZoneId storageZone) {
        for (String selector : new String[]{"meta[property=article:published_time]",
                "meta[itemprop=datePublished]", "time[itemprop=datePublished][datetime]"}) {
            for (Element element : doc.select(selector)) {
                String value = element.attr(element.tagName().equals("time") ? "datetime" : "content").strip();
                try {
                    return OffsetDateTime.parse(value).atZoneSameInstant(storageZone).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    // 날짜만 있거나 시간대가 없는 값은 시각을 추측하지 않고 다음 후보를 검사한다.
                }
            }
        }
        return null;
    }

    public static String extract(Document doc) {
        for (String selector : new String[]{"meta[property=og:image]", "meta[name=twitter:image]"}) {
            Element meta = doc.selectFirst(selector);
            if (meta == null || meta.attr("content").isBlank()) continue; // 빈 값은 absUrl이 페이지 주소로 바꿔버림
            String url = valid(meta.absUrl("content").isEmpty() ? meta.attr("content") : meta.absUrl("content"));
            if (url != null) return url;
        }
        return null;
    }

    static String valid(String raw) {
        if (raw == null) return null;
        String url = raw.strip();
        if (url.isEmpty() || url.length() > MAX_LENGTH) return null;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) return null;
            return isPlaceholder(url) ? null : url;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 로고·기본 이미지 주소인지. 경로만 검사한다(쿼리 문자열 무시). */
    public static boolean isPlaceholder(String url) {
        if (url == null) return false;
        try {
            String path = URI.create(url.strip()).getPath();
            return path != null && PLACEHOLDER_IMAGE.matcher(path).find();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 기사 제목. 한경 페이지는 섹션 로고(h1.logo-area)와 제목(h1.headline) 두 h1이 있어
     * "h1" 전체를 합치면 "경제 …"처럼 섹션명이 붙는다. headline → og:title → 첫 h1 순서로 하나만 쓴다.
     */
    public static String title(Document doc) {
        Element headline = doc.selectFirst("h1.headline");
        if (headline != null && !headline.text().isBlank()) return headline.text().strip();
        Element og = doc.selectFirst("meta[property=og:title]");
        if (og != null && !og.attr("content").isBlank()) return og.attr("content").strip();
        Element h1 = doc.selectFirst("h1");
        return h1 == null ? "" : h1.text().strip();
    }
}
