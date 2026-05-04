package com.youthletter.domain.term.service;

import com.youthletter.domain.activity.entity.TermLookupLog;
import com.youthletter.domain.activity.repository.TermLookupLogRepository;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.repository.ArticleTermHighlightRepository;
import com.youthletter.domain.term.dto.LearnedTermResponse;
import com.youthletter.domain.term.dto.TermDetailResponse;
import com.youthletter.domain.term.dto.TermListResponse;
import com.youthletter.domain.term.entity.FinancialTerm;
import com.youthletter.domain.term.repository.FinancialTermRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TermService {

    private final FinancialTermRepository termRepository;
    private final TermLookupLogRepository termLookupLogRepository;
    private final UserRepository userRepository;
    private final ArticleTermHighlightRepository highlightRepository;

    @Transactional(readOnly = true)
    public Page<TermListResponse> getTermList(String path, Pageable pageable) {
        if (StringUtils.hasText(path)) {
            return termRepository.findByPathDescendants(path, pageable)
                    .map(TermListResponse::from);
        }
        return termRepository.findAll(pageable).map(TermListResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TermListResponse> searchTerms(String keyword, int limit) {
        return termRepository.searchByText(keyword, limit)
                .stream()
                .map(TermListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TermDetailResponse getTermDetail(Long termId, Long userId) {
        FinancialTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));

        boolean isLearned = userId != null
                && termLookupLogRepository.existsByUserIdAndTermId(userId, termId);

        List<NewsArticle> relatedArticles = highlightRepository.findTop5ArticlesByTermId(termId);
        List<TermDetailResponse.RelatedArticle> articleDtos = relatedArticles.stream()
                .map(a -> new TermDetailResponse.RelatedArticle(
                        a.getId(), a.getTitle(), a.getCategory(),
                        a.getSourceName(), a.getPublishedAt()))
                .toList();

        return TermDetailResponse.of(term, isLearned, articleDtos);
    }

    /**
     * 용어 학습 완료 처리 (TERM-03)
     * term_lookup_log에 기록 (article 없이 직접 학습)
     */
    @Transactional
    public void learnTerm(Long userId, Long termId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        FinancialTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TERM_NOT_FOUND));

        termLookupLogRepository.save(TermLookupLog.builder()
                .user(user)
                .term(term)
                .article(null)
                .build());
    }

    /**
     * 내가 학습한 용어 목록 (TERM-04)
     * 용어별 가장 최근 학습 날짜 기준, 중복 제거하여 반환
     */
    @Transactional(readOnly = true)
    public List<LearnedTermResponse> getLearnedTerms(Long userId) {
        List<TermLookupLog> logs = termLookupLogRepository.findByUserIdWithTermOrderByDesc(userId);

        // 용어 ID 기준 중복 제거 (LinkedHashMap으로 순서 유지 — 최신 학습 순)
        Map<Long, TermLookupLog> latestPerTerm = new LinkedHashMap<>();
        for (TermLookupLog log : logs) {
            latestPerTerm.putIfAbsent(log.getTerm().getId(), log);
        }

        return latestPerTerm.values().stream()
                .map(log -> new LearnedTermResponse(
                        log.getTerm().getId(),
                        log.getTerm().getName(),
                        log.getTerm().getSimpleDefinition(),
                        log.getTerm().getDifficultyLevel(),
                        log.getLookedUpAt()
                ))
                .toList();
    }
}
