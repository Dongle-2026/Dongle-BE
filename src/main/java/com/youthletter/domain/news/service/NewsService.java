package com.youthletter.domain.news.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.activity.service.ActivityService;
import com.youthletter.domain.news.dto.NewsBookmarkResponse;
import com.youthletter.domain.news.dto.NewsDetailResponse;
import com.youthletter.domain.news.dto.NewsDetailResponse.TermHighlightDto;
import com.youthletter.domain.news.dto.NewsListResponse;
import com.youthletter.domain.news.entity.ArticleTermHighlight;
import com.youthletter.domain.news.entity.NewsArticle;
import com.youthletter.domain.news.entity.NewsBookmark;
import com.youthletter.domain.news.entity.NewsSummary;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.news.repository.NewsBookmarkRepository;
import com.youthletter.domain.news.repository.NewsSummaryRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsService {

    private final NewsArticleRepository articleRepository;
    private final NewsSummaryRepository summaryRepository;
    private final NewsBookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    // ── 목록 조회 ───────────────────────────────────────────────

    public Page<NewsListResponse> getNewsList(String category, Pageable pageable) {
        Page<NewsArticle> articles = (category != null && !category.isBlank())
                ? articleRepository.findByCategory(category, pageable)
                : articleRepository.findAllByOrderByPublishedAtDesc(pageable);

        return articles.map(article -> {
            String summary = summaryRepository
                    .findByArticleIdAndValidToIsNull(article.getId())
                    .map(NewsSummary::getSummaryText)
                    .orElse(null);
            return NewsListResponse.from(article, summary);
        });
    }

    // ── 상세 조회 ───────────────────────────────────────────────

    public NewsDetailResponse getNewsDetail(Long articleId) {
        NewsArticle article = findArticle(articleId);

        NewsSummary summary = summaryRepository
                .findByArticleIdAndValidToIsNull(articleId)
                .orElse(null);

        List<TermHighlightDto> highlights = entityManager
                .createQuery("""
                    SELECT h FROM ArticleTermHighlight h
                    JOIN FETCH h.term
                    WHERE h.article.id = :articleId
                    ORDER BY h.positionStart ASC
                    """, ArticleTermHighlight.class)
                .setParameter("articleId", articleId)
                .getResultList()
                .stream()
                .map(h -> TermHighlightDto.builder()
                        .termId(h.getTerm().getId())
                        .termName(h.getTerm().getName())
                        .simpleDefinition(h.getTerm().getSimpleDefinition())
                        .positionStart(h.getPositionStart())
                        .positionEnd(h.getPositionEnd())
                        .build())
                .toList();

        return NewsDetailResponse.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .sourceName(article.getSourceName())
                .sourceUrl(article.getSourceUrl())
                .category(article.getCategory())
                .publishedAt(article.getPublishedAt())
                .summary(summary != null ? summary.getSummaryText() : null)
                .modelVersion(summary != null ? summary.getModelVersion() : null)
                .termHighlights(highlights)
                .build();
    }

    // ── 오늘의 뉴스 브리핑 ─────────────────────────────────────

    public List<NewsListResponse> getTodayNews(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<NewsArticle> articles = articleRepository.findTodayArticles(
                startOfDay, PageRequest.of(0, 24));

        // 로그인 사용자는 관심 카테고리 우선 정렬
        if (userId != null) {
            List<String> interests = getUserInterests(userId);
            if (!interests.isEmpty()) {
                articles = articles.stream()
                        .sorted(Comparator.comparingInt(
                                a -> interests.contains(a.getCategory()) ? 0 : 1))
                        .toList();
            }
        }

        return articles.stream()
                .limit(12)
                .map(a -> {
                    String summary = summaryRepository
                            .findByArticleIdAndValidToIsNull(a.getId())
                            .map(NewsSummary::getSummaryText)
                            .orElse(null);
                    return NewsListResponse.from(a, summary);
                })
                .toList();
    }

    // ── 읽음 처리 ───────────────────────────────────────────────

    @Transactional
    public void markAsRead(Long userId, Long articleId, Integer durationSeconds) {
        activityService.trackReading(userId, articleId, durationSeconds);
    }

    // ── 북마크 토글 ─────────────────────────────────────────────

    @Transactional
    public boolean toggleBookmark(Long userId, Long articleId) {
        Optional<NewsBookmark> existing = bookmarkRepository.findByUserIdAndArticleId(userId, articleId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return false;  // 북마크 해제
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        NewsArticle article = findArticle(articleId);
        bookmarkRepository.save(NewsBookmark.builder().user(user).article(article).build());
        return true;  // 북마크 추가
    }

    // ── 내 북마크 목록 ─────────────────────────────────────────

    public Page<NewsBookmarkResponse> getMyBookmarks(Long userId, Pageable pageable) {
        return bookmarkRepository.findByUserIdWithArticle(userId, pageable)
                .map(NewsBookmarkResponse::from);
    }

    // ── helpers ─────────────────────────────────────────────────

    private NewsArticle findArticle(Long articleId) {
        return articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private List<String> getUserInterests(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String interests = user.getInterests();
                    if (interests == null || interests.isBlank()) return List.<String>of();
                    try {
                        return objectMapper.readValue(interests, new TypeReference<List<String>>() {});
                    } catch (Exception e) {
                        return List.<String>of();
                    }
                })
                .orElse(List.of());
    }
}
