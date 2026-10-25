package com.youthletter.domain.search.service;

import com.youthletter.domain.news.category.NewsCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 키워드 카드가 모자랄 때 채우는 카테고리별 기본 키워드(각 10개).
 * 기사 제목에서 찾기 쉽도록 띄어쓰기 없는 짧은 단어로 둔다.
 */
final class KeywordSeeds {
    private static final Map<NewsCategory, List<String>> SEEDS = new EnumMap<>(NewsCategory.class);

    static {
        SEEDS.put(NewsCategory.YOUTH, List.of("청년도약계좌", "청년월세", "청년전세대출", "청년내일채움공제", "청년주택드림",
                "국민취업지원제도", "청년수당", "학자금대출", "사회초년생", "청년미래적금"));
        SEEDS.put(NewsCategory.POLICY, List.of("기준금리", "세법개정안", "예산안", "금융위원회", "가계부채",
                "스트레스DSR", "부동산대책", "최저임금", "연금개혁", "소상공인"));
        SEEDS.put(NewsCategory.FINANCE, List.of("주택담보대출", "예대금리차", "신용점수", "DSR", "인터넷은행",
                "실손보험", "신용카드", "대환대출", "중도상환수수료", "예금자보호"));
        SEEDS.put(NewsCategory.INVESTMENT, List.of("코스피", "ETF", "배당", "공모주", "환율",
                "비트코인", "미국주식", "ISA", "반도체", "코스닥"));
        SEEDS.put(NewsCategory.REAL_ESTATE, List.of("청약", "전세사기", "아파트값", "전세가율", "재건축",
                "분양가", "LTV", "주택공급", "월세", "공시가격"));
        SEEDS.put(NewsCategory.ECONOMY, List.of("물가", "소비자물가", "금리", "환율", "성장률",
                "수출", "경상수지", "고용률", "인플레이션", "경기침체"));
        SEEDS.put(NewsCategory.JOB, List.of("채용", "공채", "고용률", "청년고용", "최저임금",
                "실업급여", "인턴", "이직", "일자리", "신입사원"));
        SEEDS.put(NewsCategory.SAVING, List.of("적금", "예금", "파킹통장", "청년도약계좌", "고금리",
                "비상금", "저축은행", "예금금리", "CMA", "목돈"));
    }

    private KeywordSeeds() {}

    static List<String> of(NewsCategory category) {
        return SEEDS.get(category);
    }

    /** 카테고리 구분 없이: 카테고리 순서대로 하나씩 번갈아(같은 단어는 처음 것만). */
    static List<Seed> interleaved() {
        List<Seed> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++)
            for (NewsCategory c : NewsCategory.values()) {
                String k = SEEDS.get(c).get(i);
                if (seen.add(k)) out.add(new Seed(k, c));
            }
        return out;
    }

    record Seed(String keyword, NewsCategory category) {}
}
