package com.youthletter.domain.cardnews.service;

import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsCard;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.domain.news.entity.NewsArticle;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 서비스 연동 전 사용하는 stub 카드뉴스 생성기.
 * 기사 제목과 카테고리를 기반으로 7장짜리 카드 시나리오를 생성한다.
 * AI 연동 완료 후 AiServiceClient 호출 로직으로 교체된다.
 */
@Component
public class CardNewsStubGenerator {

    private static final int MAX_TITLE_LEN = 18;
    private static final int MAX_BODY_LEN = 35;
    private static final int MAX_SUB_BODY_LEN = 45;

    public List<CardNewsCard> generate(CardNews cardNews, NewsArticle article) {
        String articleTitle = truncate(article.getTitle(), MAX_TITLE_LEN);
        String category = article.getCategory();

        return List.of(
            buildCard(cardNews, 1, CardTemplateType.T01,
                    articleTitle,
                    "지금 꼭 알아야 할 경제 뉴스를 쉽게 알려드릴게요.",
                    null,
                    null),

            buildCard(cardNews, 2, CardTemplateType.T02,
                    "핵심 요약",
                    truncate(article.getTitle() + "에 관한 주요 내용입니다.", MAX_BODY_LEN),
                    "기사 전체를 읽지 않아도 핵심을 파악할 수 있어요.",
                    null),

            buildCard(cardNews, 3, CardTemplateType.T03,
                    "개념 이해",
                    category + " 분야의 핵심 개념을 살펴볼게요.",
                    "어려운 금융 용어도 쉽게 설명해 드릴게요.",
                    category),

            buildCard(cardNews, 4, CardTemplateType.T04,
                    "수치로 보기",
                    "이번 기사에서 가장 중요한 수치를 확인해요.",
                    "숫자 하나가 우리 생활에 미치는 영향을 알 수 있어요.",
                    null),

            buildCard(cardNews, 5, CardTemplateType.T06,
                    "왜 이런 일이?",
                    "이번 변화가 생긴 배경과 원인을 알아볼게요.",
                    "원인을 알면 앞으로의 변화도 예측할 수 있어요.",
                    null),

            buildCard(cardNews, 6, CardTemplateType.T07,
                    "나에게 미치는 영향",
                    "이 뉴스가 청년에게 어떤 의미인지 알아볼게요.",
                    "금리·물가·취업 등 내 삶과 연결 지어 생각해봐요.",
                    null),

            buildCard(cardNews, 7, CardTemplateType.T10,
                    "오늘 배운 내용 정리",
                    "원문을 읽거나 퀴즈로 이해도를 확인해보세요.",
                    "꾸준한 학습이 경제 감각을 키워줍니다.",
                    null)
        );
    }

    private CardNewsCard buildCard(CardNews cardNews, int order, CardTemplateType type,
                                   String title, String body, String subBody, String highlight) {
        return CardNewsCard.builder()
                .cardNews(cardNews)
                .cardOrder(order)
                .templateType(type)
                .title(title != null ? truncate(title, MAX_TITLE_LEN) : null)
                .body(body != null ? truncate(body, MAX_BODY_LEN) : null)
                .subBody(subBody != null ? truncate(subBody, MAX_SUB_BODY_LEN) : null)
                .highlight(highlight)
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }
}
