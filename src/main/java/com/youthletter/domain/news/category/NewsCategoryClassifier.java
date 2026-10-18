package com.youthletter.domain.news.category;

import java.util.Locale;

/** 제목·본문 키워드 점수로 카테고리를 정한다. 외부 호출 없이 결정적으로 동작한다. */
public final class NewsCategoryClassifier {
    private static final int TITLE_WEIGHT = 3;

    private NewsCategoryClassifier() {}

    public static NewsCategory classify(String title, String content) {
        return match(title, content).orElse(NewsCategory.FINANCE);
    }

    /** 키워드가 하나도 맞지 않으면 비어 있다(호출자가 다른 근거로 정할 수 있게). */
    public static java.util.Optional<NewsCategory> match(String title, String content) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String c = content == null ? "" : content.toLowerCase(Locale.ROOT);
        NewsCategory best = null;
        int bestScore = 0;
        for (NewsCategory category : NewsCategory.values()) {
            int score = 0;
            for (String keyword : category.keywords()) {
                score += TITLE_WEIGHT * occurrences(t, keyword) + occurrences(c, keyword);
            }
            if (score > bestScore) {
                best = category;
                bestScore = score;
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    private static int occurrences(String text, String keyword) {
        int count = 0;
        for (int i = text.indexOf(keyword); i >= 0; i = text.indexOf(keyword, i + keyword.length())) count++;
        return count;
    }
}
