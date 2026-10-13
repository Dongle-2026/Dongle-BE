package com.youthletter.domain.news.service;

/** AI stub 모드가 만든 가짜 요약 판별. 이미 저장된 stub 요약도 응답에서 숨긴다. */
public final class NewsSummaryText {
    private NewsSummaryText() {}

    public static boolean isStub(String summary) {
        return summary != null && summary.startsWith("[STUB]");
    }

    /** 화면에 보여줄 요약. 없거나 stub이면 null(필드 생략). */
    public static String display(String summary) {
        return summary == null || summary.isBlank() || isStub(summary) ? null : summary;
    }
}
