package com.youthletter.domain.spending.service;

import com.youthletter.domain.spending.dto.*;
import com.youthletter.domain.spending.entity.SpendingBudget;
import com.youthletter.domain.spending.entity.SpendingEvent;
import com.youthletter.domain.spending.entity.SpendingSnapshot;
import com.youthletter.domain.spending.repository.*;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpendingServiceTest {

    @Mock SpendingEventRepository eventRepository;
    @Mock SpendingSnapshotRepository snapshotRepository;
    @Mock SpendingBudgetRepository budgetRepository;
    @Mock SpendingInsightRepository insightRepository;
    @Mock UserRepository userRepository;
    @Mock com.youthletter.domain.news.client.AiServiceClient aiClient;

    @InjectMocks SpendingService spendingService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .email("test@example.com")
                .name("테스트")
                .nickname("tester")
                .role("USER")
                .build();
        ReflectionTestUtils.setField(sampleUser, "id", 1L);
    }

    // ── createSpending ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createSpending")
    class CreateSpending {

        @Test
        @DisplayName("정상 등록 - CREATED 이벤트가 저장되고 spendingId가 반환된다")
        void success() {
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(eventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SpendingCreateRequest req = new SpendingCreateRequest(
                    BigDecimal.valueOf(15000), "FOOD", "점심", LocalDate.now(), null);

            Long id = spendingService.createSpending(1L, req);

            assertThat(id).isPositive();
            verify(eventRepository).save(argThat(e -> "CREATED".equals(e.getEventType())));
        }

        @Test
        @DisplayName("존재하지 않는 유저 - USER_NOT_FOUND 예외")
        void userNotFound() {
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            SpendingCreateRequest req = new SpendingCreateRequest(
                    BigDecimal.valueOf(5000), "TRANSPORT", null, LocalDate.now(), null);

            assertThatThrownBy(() -> spendingService.createSpending(99L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
        }
    }

    // ── updateSpending ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateSpending")
    class UpdateSpending {

        @Test
        @DisplayName("정상 수정 - UPDATED 이벤트가 저장된다")
        void success() {
            SpendingEvent original = buildEvent(1L, "CREATED", BigDecimal.valueOf(10000), "FOOD");
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(eventRepository.findBySpendingIdOrderByCreatedAtAsc(100L))
                    .willReturn(List.of(original));
            given(eventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            SpendingCreateRequest req = new SpendingCreateRequest(
                    BigDecimal.valueOf(12000), "FOOD", "저녁", LocalDate.now(), null);

            spendingService.updateSpending(1L, 100L, req);

            verify(eventRepository).save(argThat(e -> "UPDATED".equals(e.getEventType())));
        }

        @Test
        @DisplayName("존재하지 않는 소비 ID - SPENDING_NOT_FOUND 예외")
        void notFound() {
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(eventRepository.findBySpendingIdOrderByCreatedAtAsc(999L))
                    .willReturn(List.of());

            SpendingCreateRequest req = new SpendingCreateRequest(
                    BigDecimal.valueOf(5000), "ETC", null, LocalDate.now(), null);

            assertThatThrownBy(() -> spendingService.updateSpending(1L, 999L, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.SPENDING_NOT_FOUND.getMessage());
        }
    }

    // ── deleteSpending ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteSpending")
    class DeleteSpending {

        @Test
        @DisplayName("정상 삭제 - DELETED 이벤트가 저장된다")
        void success() {
            SpendingEvent original = buildEvent(1L, "CREATED", BigDecimal.valueOf(20000), "SHOPPING");
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(eventRepository.findBySpendingIdOrderByCreatedAtAsc(200L))
                    .willReturn(List.of(original));
            given(eventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            spendingService.deleteSpending(1L, 200L);

            verify(eventRepository).save(argThat(e -> "DELETED".equals(e.getEventType())));
        }
    }

    // ── getDashboard ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDashboard")
    class GetDashboard {

        @Test
        @DisplayName("스냅샷 없을 때 - 이벤트에서 집계한 결과 반환")
        void aggregateFromEvents() {
            given(eventRepository.aggregateByCategory(eq(1L), any(), any()))
                    .willReturn(List.of(
                            new Object[]{"FOOD", BigDecimal.valueOf(150000)},
                            new Object[]{"TRANSPORT", BigDecimal.valueOf(50000)}
                    ));

            SpendingDashboardResponse resp = spendingService.getDashboard(1L, "2026-05");

            assertThat(resp.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(200000));
            assertThat(resp.categoryTotals()).containsKeys("FOOD", "TRANSPORT");
        }
    }

    // ── getTrend ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTrend")
    class GetTrend {

        @Test
        @DisplayName("6개월 트렌드 반환 - 전월 대비 증감 계산")
        void success() {
            given(eventRepository.findMonthlyTrend(1L)).willReturn(List.of(
                    new Object[]{"2026-03", 300000L, null},
                    new Object[]{"2026-04", 350000L, 300000L},
                    new Object[]{"2026-05", 200000L, 350000L}
            ));

            SpendingTrendResponse trend = spendingService.getTrend(1L);

            assertThat(trend.months()).hasSize(3);
            assertThat(trend.months().get(0).changeFromPrev()).isNull();
            assertThat(trend.months().get(1).changeFromPrev())
                    .isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat(trend.months().get(2).changeFromPrev())
                    .isEqualByComparingTo(BigDecimal.valueOf(-150000));
        }
    }

    // ── upsertBudget ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upsertBudget")
    class UpsertBudget {

        @Test
        @DisplayName("예산 최초 설정 - 새 SpendingBudget 저장")
        void createNew() {
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(budgetRepository.findByUserId(1L)).willReturn(Optional.empty());
            given(budgetRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            spendingService.upsertBudget(1L, new SpendingBudgetRequest(500_000L));

            verify(budgetRepository).save(any(SpendingBudget.class));
        }

        @Test
        @DisplayName("예산 수정 - update() 호출")
        void update() {
            SpendingBudget existing = SpendingBudget.builder()
                    .user(sampleUser).monthlyBudget(300_000L).build();
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            given(budgetRepository.findByUserId(1L)).willReturn(Optional.of(existing));

            spendingService.upsertBudget(1L, new SpendingBudgetRequest(600_000L));

            assertThat(existing.getMonthlyBudget()).isEqualTo(600_000L);
        }
    }

    // ── getAiInsight ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("AI 인사이트 stub - 분석 기간과 인사이트 텍스트 포함")
    void aiInsightStub() {
        SpendingAiInsightResponse resp = spendingService.getAiInsight(1L);

        assertThat(resp.insightText()).isNotBlank();
        assertThat(resp.modelVersion()).isEqualTo("rule-based-v1");
        assertThat(resp.insightText()).contains("0원").doesNotContain("15%");
        assertThat(resp.analysisEnd()).isEqualTo(LocalDate.now());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private SpendingEvent buildEvent(Long userId, String type, BigDecimal amount, String category) {
        return SpendingEvent.builder()
                .user(sampleUser)
                .eventType(type)
                .spendingId(100L)
                .amount(amount)
                .category(category)
                .spentAt(LocalDate.now())
                .build();
    }

    @Test void aiFailureUsesActualTotals() {
        given(eventRepository.aggregateByCategory(eq(1L), any(), any())).willReturn(
                java.util.Collections.singletonList(new Object[]{"FOOD", BigDecimal.valueOf(12000)}));
        given(aiClient.insight(any())).willThrow(new BusinessException(ErrorCode.AI_SERVICE_ERROR));
        var result = spendingService.getAiInsight(1L);
        assertThat(result.insightText()).contains("12000원");
        assertThat(result.modelVersion()).isEqualTo("rule-based-v1");
    }

    @Test void numericAdviceIsRejected() {
        given(eventRepository.aggregateByCategory(eq(1L), any(), any())).willReturn(
                java.util.Collections.singletonList(new Object[]{"FOOD", BigDecimal.valueOf(20000)}));
        given(aiClient.insight(any())).willReturn(new com.youthletter.domain.news.client.dto.AiInsightResponse("99% 줄이세요", "qwen3:8b"));
        var result = spendingService.getAiInsight(1L);
        assertThat(result.insightText()).contains("20000원").doesNotContain("99%");
        assertThat(result.modelVersion()).isEqualTo("rule-based-v1");
    }
}
