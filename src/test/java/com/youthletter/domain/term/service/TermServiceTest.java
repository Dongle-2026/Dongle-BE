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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock FinancialTermRepository termRepository;
    @Mock TermLookupLogRepository termLookupLogRepository;
    @Mock UserRepository userRepository;
    @Mock ArticleTermHighlightRepository highlightRepository;

    @InjectMocks TermService termService;

    private FinancialTerm sampleTerm;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleTerm = FinancialTerm.builder()
                .name("ETF")
                .path("finance.stock.etf")
                .definition("Exchange Traded Fund의 약자로 특정 지수를 추종하는 상품")
                .simpleDefinition("여러 주식을 한 번에 묶어서 사고파는 장바구니 같은 상품")
                .difficultyLevel(2)
                .build();

        sampleUser = User.builder()
                .email("test@test.com")
                .name("테스트")
                .nickname("tester")
                .build();
    }

    @Nested
    @DisplayName("용어 목록 조회 (getTermList)")
    class GetTermList {

        @Test
        @DisplayName("path 없으면 전체 용어 페이지 반환")
        void noPath_returnsAllTerms() {
            Pageable pageable = PageRequest.of(0, 20);
            given(termRepository.findAll(pageable))
                    .willReturn(new PageImpl<>(List.of(sampleTerm)));

            Page<TermListResponse> result = termService.getTermList(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("ETF");
        }

        @Test
        @DisplayName("path 있으면 ltree 하위 용어 조회")
        void withPath_returnsDescendants() {
            Pageable pageable = PageRequest.of(0, 20);
            given(termRepository.findByPathDescendants("finance.stock", pageable))
                    .willReturn(new PageImpl<>(List.of(sampleTerm)));

            Page<TermListResponse> result = termService.getTermList("finance.stock", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(termRepository).findByPathDescendants("finance.stock", pageable);
        }
    }

    @Nested
    @DisplayName("용어 검색 (searchTerms)")
    class SearchTerms {

        @Test
        @DisplayName("키워드로 tsvector 검색 결과 반환")
        void searchByKeyword_returnsList() {
            given(termRepository.searchByText("ETF", 10))
                    .willReturn(List.of(sampleTerm));

            List<TermListResponse> result = termService.searchTerms("ETF", 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("ETF");
        }

        @Test
        @DisplayName("검색 결과 없으면 빈 리스트 반환")
        void noResult_returnsEmptyList() {
            given(termRepository.searchByText("존재하지않는용어", 10)).willReturn(List.of());

            List<TermListResponse> result = termService.searchTerms("존재하지않는용어", 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("용어 상세 조회 (getTermDetail)")
    class GetTermDetail {

        @Test
        @DisplayName("비로그인 상태 — isLearned false")
        void anonymousUser_isLearnedFalse() {
            given(termRepository.findById(1L)).willReturn(Optional.of(sampleTerm));
            given(highlightRepository.findTop5ArticlesByTermId(1L)).willReturn(List.of());

            TermDetailResponse result = termService.getTermDetail(1L, null);

            assertThat(result.isLearned()).isFalse();
            assertThat(result.name()).isEqualTo("ETF");
        }

        @Test
        @DisplayName("로그인 상태, 학습 완료한 용어 — isLearned true")
        void loggedInUser_alreadyLearned_isLearnedTrue() {
            given(termRepository.findById(1L)).willReturn(Optional.of(sampleTerm));
            given(termLookupLogRepository.existsByUserIdAndTermId(10L, 1L)).willReturn(true);
            given(highlightRepository.findTop5ArticlesByTermId(1L)).willReturn(List.of());

            TermDetailResponse result = termService.getTermDetail(1L, 10L);

            assertThat(result.isLearned()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 termId — TERM_NOT_FOUND 예외")
        void notFound_throwsException() {
            given(termRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termService.getTermDetail(999L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.TERM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("관련 뉴스 기사 최대 5건 포함")
        void includesRelatedArticles() {
            NewsArticle article = NewsArticle.builder()
                    .sourceUrl("https://test.com/article/1")
                    .sourceName("한경")
                    .title("ETF 완전 정복")
                    .content("본문")
                    .category("INVEST")
                    .build();

            given(termRepository.findById(1L)).willReturn(Optional.of(sampleTerm));
            given(highlightRepository.findTop5ArticlesByTermId(1L)).willReturn(List.of(article));

            TermDetailResponse result = termService.getTermDetail(1L, null);

            assertThat(result.relatedArticles()).hasSize(1);
            assertThat(result.relatedArticles().get(0).title()).isEqualTo("ETF 완전 정복");
        }
    }

    @Nested
    @DisplayName("용어 학습 처리 (learnTerm)")
    class LearnTerm {

        @Test
        @DisplayName("정상 학습 처리 — TermLookupLog 저장")
        void learnTerm_savesLog() {
            given(userRepository.findById(10L)).willReturn(Optional.of(sampleUser));
            given(termRepository.findById(1L)).willReturn(Optional.of(sampleTerm));

            termService.learnTerm(10L, 1L);

            verify(termLookupLogRepository).save(any(TermLookupLog.class));
        }

        @Test
        @DisplayName("존재하지 않는 userId — USER_NOT_FOUND 예외")
        void userNotFound_throwsException() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termService.learnTerm(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 termId — TERM_NOT_FOUND 예외")
        void termNotFound_throwsException() {
            given(userRepository.findById(10L)).willReturn(Optional.of(sampleUser));
            given(termRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> termService.learnTerm(10L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.TERM_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("학습한 용어 목록 (getLearnedTerms)")
    class GetLearnedTerms {

        @Test
        @DisplayName("동일 용어 중복 제거 — 최신 학습 순 반환")
        void deduplicateByTermId_returnsLatestFirst() {
            TermLookupLog log1 = TermLookupLog.builder().user(sampleUser).term(sampleTerm).article(null).build();
            TermLookupLog log2 = TermLookupLog.builder().user(sampleUser).term(sampleTerm).article(null).build();
            given(termLookupLogRepository.findByUserIdWithTermOrderByDesc(10L))
                    .willReturn(List.of(log1, log2));

            List<LearnedTermResponse> result = termService.getLearnedTerms(10L);

            // 동일 term이라도 중복 제거되어 1건만 반환
            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("ETF");
        }

        @Test
        @DisplayName("학습 이력 없으면 빈 리스트")
        void noHistory_returnsEmpty() {
            given(termLookupLogRepository.findByUserIdWithTermOrderByDesc(10L))
                    .willReturn(List.of());

            List<LearnedTermResponse> result = termService.getLearnedTerms(10L);

            assertThat(result).isEmpty();
        }
    }
}
