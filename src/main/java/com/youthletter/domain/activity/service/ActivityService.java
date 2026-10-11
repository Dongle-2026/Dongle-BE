package com.youthletter.domain.activity.service;

import com.youthletter.domain.activity.dto.ReadHistoryResponse;
import com.youthletter.domain.activity.dto.ReadHistoryRow;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final com.youthletter.domain.quiz.service.HoneyService honeyService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 뉴스 읽기 기록 저장 (프론트에서 뉴스 상세 진입/이탈 시 호출)
     */
    @Transactional
    public void trackReading(Long userId, Long articleId, Integer durationSeconds) {
        // Serialize writes for a user so simultaneous retries cannot both insert.
        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        NewsArticle article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));

        readingHistoryRepository.insertDailyReading(user.getId(), article.getId(), durationSeconds);
        honeyService.awardRead(user.getId(), article.getId()); // 기사당 하루 1회 5꿀
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

    /**
     * 최근 읽은 기사 (기사별 1건, 마지막 읽은 시각 내림차순).
     * 정렬은 쿼리에 고정하므로 클라이언트 sort 파라미터는 무시한다.
     */
    @Transactional(readOnly = true)
    public Page<ReadHistoryResponse> getReadHistory(Long userId, Pageable pageable) {
        Page<ReadHistoryRow> rows = readingHistoryRepository.findReadHistory(userId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        java.util.Map<Long, Long> honey = new java.util.HashMap<>();
        if (rows.hasContent()) {
            Long[] ids = rows.getContent().stream().map(ReadHistoryRow::articleId).toArray(Long[]::new);
            jdbc.query("SELECT article_id, sum(amount) AS honey FROM honey_ledger WHERE user_id=? AND article_id = ANY(?) GROUP BY article_id",
                    rs -> { honey.put(rs.getLong("article_id"), rs.getLong("honey")); }, userId, ids);
        }
        return rows.map(row -> ReadHistoryResponse.of(row, honey.getOrDefault(row.articleId(), 0L)));
    }
}
