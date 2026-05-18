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

    // ── 생성 ──────────────────────────────────────────────────────

    @Transactional
    public CardNewsGenerateResponse generate(Long articleId) {
        NewsArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        // 이미 생성된 카드뉴스가 있으면 재생성(기존 삭제 후 재생성)
        cardNewsRepository.findByArticleId(articleId)
                .ifPresent(cardNewsRepository::delete);

        CardNews cardNews = CardNews.builder()
                .article(article)
                .totalCards(0)
                .status(CardNewsStatus.DRAFT)
                .build();
        cardNewsRepository.save(cardNews);

        // stub 모드: AI 연동 완료 후 aiServiceClient 호출로 교체
        List<CardNewsCard> cards = stubGenerator.generate(cardNews, article);
        cardNewsCardRepository.saveAll(cards);

        cardNews.updateTotalCards(cards.size());
        cardNews.publish();

        log.info("카드뉴스 생성 완료: articleId={}, totalCards={}", articleId, cards.size());
        return CardNewsGenerateResponse.from(cardNews);
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
