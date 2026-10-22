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

    /** 카테고리 키워드에 없는 금융·경제 단서. 관련성 판단에만 쓴다(분류에는 쓰지 않음). */
    private static final java.util.List<String> FINANCE_HINTS = java.util.List.of(
            "주가", "매출", "영업이익", "실적", "시가총액", "세금", "세율", "법인세", "예산", "재정", "부채", "채권",
            "연금", "소득", "임금", "원화", "달러", "보험료", "신용", "자산", "이자", "수익", "손실", "적자", "흑자",
            "레버리지", "상한가", "하한가", "급등", "급락", "공매도", "기업", "시장");

    /** 가장 높은 카테고리 점수(제목 3점, 본문 1점). 금융·경제 기사인지 거르는 데 쓴다. */
    public static int relevance(String title, String content) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String c = content == null ? "" : content.toLowerCase(Locale.ROOT);
        int best = 0;
        int hints = 0;
        for (String keyword : FINANCE_HINTS) hints += TITLE_WEIGHT * occurrences(t, keyword) + occurrences(c, keyword);
        best = hints;
        for (NewsCategory category : NewsCategory.values()) {
            int score = 0;
            for (String keyword : category.keywords()) {
                score += TITLE_WEIGHT * occurrences(t, keyword) + occurrences(c, keyword);
            }
            best = Math.max(best, score);
        }
        return best;
    }

    private static int occurrences(String text, String keyword) {
        int count = 0;
        for (int i = text.indexOf(keyword); i >= 0; i = text.indexOf(keyword, i + keyword.length())) count++;
        return count;
    }
}
