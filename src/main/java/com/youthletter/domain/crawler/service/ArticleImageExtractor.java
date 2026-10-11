package com.youthletter.domain.crawler.service;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;

/** 기사 페이지의 대표 이미지(og:image → twitter:image) URL. http(s) 절대 주소만 허용한다. */
public final class ArticleImageExtractor {
    static final int MAX_LENGTH = 1000;

    private ArticleImageExtractor() {}

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
            return url;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
