package com.youthletter.domain.cardnews.service;

import com.youthletter.domain.cardnews.dto.CardNewsGenerateResponse;
import com.youthletter.domain.cardnews.entity.*;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.global.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardNewsWriter {
    private final CardNewsRepository repository;
    private final EntityManager entityManager;
    private final org.springframework.core.env.Environment environment;

    @Transactional
    public CardNewsGenerateResponse save(Long articleId, AiCardResponse result) {
        boolean stub = "stub".equals(result.modelVersion());
        if (stub && (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))
                || !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test")))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        // 기사 행으로 직렬화하여 처음 생성하는 두 요청도 중복 카드뉴스를 만들지 않는다.
        NewsArticle article = entityManager.find(NewsArticle.class, articleId, LockModeType.PESSIMISTIC_WRITE);
        if (article == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        CardNews news = repository.findByArticleId(articleId).orElseGet(() -> repository.save(
                CardNews.builder().article(article).status(CardNewsStatus.DRAFT).totalCards(0).build()));
        // 개발용 스텁이 실제 생성물(출처가 없는 기존 카드 포함)을 덮어쓰지 못하게 한다.
        if (stub && !news.getCards().isEmpty() && !"stub".equals(news.getModelVersion())) {
            throw new BusinessException(ErrorCode.CARD_NEWS_ALREADY_EXISTS);
        }
        // 기존 UNIQUE(card_news_id, card_order)와 충돌하지 않도록 삭제를 먼저 flush한다.
        news.replaceCards(java.util.List.of());
        repository.flush();
        var cards = result.cards().stream().map(c -> {
            var card = CardNewsCard.builder().cardNews(news).cardOrder(c.cardOrder())
                    .templateType(c.templateType()).title(c.title()).body(c.body())
                    .subBody(c.subBody()).highlight(c.highlight()).build();
            card.setSourceSentenceIds(c.sourceSentenceIds());
            return card;
        }).toList();
        news.replaceCards(cards);
        news.recordGeneration(result.modelVersion(), result.promptVersion());
        news.publish();
        repository.saveAndFlush(news);
        return CardNewsGenerateResponse.from(news);
    }
}
