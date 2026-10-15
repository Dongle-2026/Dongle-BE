package com.youthletter.domain.cardnews.service;

import com.youthletter.domain.cardnews.dto.CardNewsDetailResponse;
import com.youthletter.domain.cardnews.dto.CardNewsGenerateResponse;
import com.youthletter.domain.cardnews.dto.CardNewsListResponse;
import com.youthletter.domain.cardnews.entity.CardNews;
import com.youthletter.domain.cardnews.entity.CardNewsCard;
import com.youthletter.domain.cardnews.entity.CardNewsStatus;
import com.youthletter.domain.cardnews.repository.CardNewsCardRepository;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardNewsService {

    private final CardNewsRepository cardNewsRepository;
    private final CardNewsCardRepository cardNewsCardRepository;
    private final NewsArticleRepository articleRepository;
    private final CardNewsStubGenerator stubGenerator;
    private final com.youthletter.domain.news.client.AiServiceClient aiClient;
    private final CardNewsValidator validator;
    private final CardNewsWriter writer;

    // ── 생성 ──────────────────────────────────────────────────────

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public CardNewsGenerateResponse generate(Long articleId) {
        NewsArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // stub 모드: AI Service 없이 개발하는 팀원을 위해 정적 카드로 동일한 저장 흐름을 사용한다.
        if (aiClient.isStubMode()) {
            var stubCards = stubGenerator.generate(null, article).stream()
                    .map(c -> new com.youthletter.domain.news.client.dto.AiCardResponse.Card(
                            c.getCardOrder(), c.getTemplateType(), c.getTitle(), c.getBody(),
                            c.getSubBody(), c.getHighlight(), List.of()))
                    .toList();
            return writer.save(articleId, new com.youthletter.domain.news.client.dto.AiCardResponse("stub", "stub", stubCards));
        }

        var result = aiClient.generateCards(new com.youthletter.domain.news.client.dto.AiProcessRequest(
                article.getSourceUrl(), article.getTitle(), article.getContent(), article.getCategory()));
        validator.validate(result, article.getTitle(), article.getContent());
        return writer.save(articleId, result);
    }

    // ── 목록 조회 ─────────────────────────────────────────────────

    public Page<CardNewsListResponse> getList(Pageable pageable) {
        return cardNewsRepository.findAllByStatus(CardNewsStatus.PUBLISHED, pageable)
                .map(CardNewsListResponse::from);
    }

    // ── 상세 조회 ─────────────────────────────────────────────────

    public CardNewsDetailResponse getDetail(Long articleId) {
        CardNews cardNews = cardNewsRepository.findByArticleId(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NEWS_NOT_FOUND));
        return CardNewsDetailResponse.from(cardNews);
    }

    // ── 단일 카드 조회 ────────────────────────────────────────────

    public CardNewsDetailResponse getDetailById(Long cardNewsId) {
        CardNews cardNews = cardNewsRepository.findById(cardNewsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARD_NEWS_NOT_FOUND));
        return CardNewsDetailResponse.from(cardNews);
    }
}
