package com.youthletter.domain.news.category;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 앱 카테고리 8종. 기사(news_articles.category)에는 표시명(name)을 저장한다(기존 '금융' 데이터와 호환).
 * keywords는 자동 분류 규칙(제목 3점, 본문 1점; 최고점 카테고리, 동점은 선언 순서, 0점이면 금융).
 */
public enum NewsCategory {
    YOUTH("youth", "청년", List.of("청년", "대학생", "사회초년생", "2030", "mz세대", "청년도약", "청년미래")),
    POLICY("policy", "정책", List.of("정부", "정책", "지원금", "지원사업", "법안", "국회", "금융위", "기재부", "제도", "시행령")),
    FINANCE("finance", "금융", List.of("은행", "대출", "보험", "카드사", "금융", "핀테크")),
    INVESTMENT("investment", "투자", List.of("주식", "증시", "코스피", "코스닥", "etf", "펀드", "투자", "상장", "배당", "비트코인", "가상자산")),
    REAL_ESTATE("real-estate", "부동산", List.of("부동산", "아파트", "주택", "전세", "월세", "청약", "분양", "재건축", "pf")),
    ECONOMY("economy", "경제", List.of("경제", "성장률", "물가", "인플레이션", "금리", "환율", "gdp", "수출", "경기", "한국은행")),
    JOB("job", "취업", List.of("취업", "채용", "고용", "일자리", "구직", "실업", "인턴")),
    SAVING("saving", "저축", List.of("저축", "적금", "예금", "통장", "목돈", "저금"));

    private final String id;
    private final String displayName;
    private final List<String> keywords;

    NewsCategory(String id, String displayName, List<String> keywords) {
        this.id = id;
        this.displayName = displayName;
        this.keywords = keywords;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    List<String> keywords() { return keywords; }

    /** id(youth) 또는 표시명(청년) → 카테고리 */
    public static Optional<NewsCategory> resolve(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(c -> c.id.equalsIgnoreCase(value) || c.displayName.equals(value)).findFirst();
    }
}
