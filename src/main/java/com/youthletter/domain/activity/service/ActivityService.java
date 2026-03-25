package com.youthletter.domain.activity.service;

import com.youthletter.domain.activity.entity.ReadingHistory;
import com.youthletter.domain.activity.entity.TermLookupLog;
import com.youthletter.domain.activity.repository.ReadingHistoryRepository;
import com.youthletter.domain.activity.repository.TermLookupLogRepository;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ReadingHistoryRepository readingHistoryRepository;
    private final TermLookupLogRepository termLookupLogRepository;
    private final UserRepository userRepository;
    private final NewsArticleRepository articleRepository;
    private final FinancialTermRepository termRepository;

    /**
     * 뉴스 읽기 기록 저장 (프론트에서 뉴스 상세 진입/이탈 시 호출)
     */
    @Transactional
    public void trackReading(Long userId, Long articleId, Integer durationSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        NewsArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        readingHistoryRepository.save(ReadingHistory.builder()
                .user(user)
                .article(article)
                .readDurationSeconds(durationSeconds)
                .build());
    }

    /**
     * 용어 조회 기록 저장 (프론트에서 용어 팝업 클릭 시 호출)
     */
    @Transactional
    public void trackTermLookup(Long userId, Long termId, Long articleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FinancialTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));

        NewsArticle article = articleId != null
                ? articleRepository.findById(articleId).orElse(null)
                : null;

        termLookupLogRepository.save(TermLookupLog.builder()
                .user(user)
                .term(term)
                .article(article)
                .build());
    }
}
