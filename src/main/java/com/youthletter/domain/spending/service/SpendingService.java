package com.youthletter.domain.spending.service;

import com.youthletter.domain.spending.dto.*;
import com.youthletter.domain.spending.entity.*;
import com.youthletter.domain.spending.repository.*;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingService {

    private final SpendingEventRepository eventRepository;
    private final SpendingSnapshotRepository snapshotRepository;
    private final SpendingBudgetRepository budgetRepository;
    private final SpendingInsightRepository insightRepository;
    private final UserRepository userRepository;
    private final com.youthletter.domain.news.client.AiServiceClient aiClient;

    private final AtomicLong spendingIdGenerator = new AtomicLong(System.currentTimeMillis());

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Transactional
    public Long createSpending(Long userId, SpendingCreateRequest request) {
        User user = findUser(userId);
        long spendingId = spendingIdGenerator.incrementAndGet();

        eventRepository.save(SpendingEvent.builder()
                .user(user)
                .eventType("CREATED")
                .spendingId(spendingId)
                .amount(request.amount())
                .category(request.category())
                .description(request.description())
                .spentAt(request.spentAt())
                .metadata(request.metadata())
                .build());

        return spendingId;
    }

    @Transactional
    public void updateSpending(Long userId, Long spendingId, SpendingCreateRequest request) {
        User user = findUser(userId);

        List<SpendingEvent> events = eventRepository.findBySpendingIdOrderByCreatedAtAsc(spendingId);
        if (events.isEmpty()) throw new BusinessException(ErrorCode.SPENDING_NOT_FOUND);
        if (!events.get(0).getUser().getId().equals(userId)) throw new BusinessException(ErrorCode.SPENDING_NOT_OWNED);

        eventRepository.save(SpendingEvent.builder()
                .user(user)
                .eventType("UPDATED")
                .spendingId(spendingId)
                .amount(request.amount())
                .category(request.category())
                .description(request.description())
                .spentAt(request.spentAt())
                .metadata(request.metadata())
                .build());
    }

    @Transactional
    public void deleteSpending(Long userId, Long spendingId) {
        User user = findUser(userId);

        List<SpendingEvent> events = eventRepository.findBySpendingIdOrderByCreatedAtAsc(spendingId);
        if (events.isEmpty()) throw new BusinessException(ErrorCode.SPENDING_NOT_FOUND);
        if (!events.get(0).getUser().getId().equals(userId)) throw new BusinessException(ErrorCode.SPENDING_NOT_OWNED);

        SpendingEvent latest = events.get(events.size() - 1);
        eventRepository.save(SpendingEvent.builder()
                .user(user)
                .eventType("DELETED")
                .spendingId(spendingId)
                .amount(latest.getAmount())
                .category(latest.getCategory())
                .description(latest.getDescription())
                .spentAt(latest.getSpentAt())
                .build());
    }

    // ── 조회 ──────────────────────────────────────────────────────────────────

    public List<SpendingListResponse> getMonthlyList(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        return eventRepository.findCurrentStateByUserAndPeriod(userId, start, end).stream()
                .filter(e -> !"DELETED".equals(e.getEventType()))
                .map(SpendingListResponse::from)
                .toList();
    }

    public SpendingDashboardResponse getDashboard(Long userId, String month) {
        // 스냅샷 갱신 파이프라인이 없으므로 수정 후 오래된 합계를 반환하지 않는다.
        YearMonth ym = YearMonth.parse(month);
        List<Object[]> aggregated = eventRepository
                .aggregateByCategory(userId, ym.atDay(1), ym.atEndOfMonth());

        return SpendingDashboardResponse.fromAggregation(month, aggregated);
    }

    public SpendingTrendResponse getTrend(Long userId) {
        List<Object[]> rows = eventRepository.findMonthlyTrend(userId);
        return SpendingTrendResponse.from(rows);
    }

    // ── 예산 ──────────────────────────────────────────────────────────────────

    @Transactional
    public void upsertBudget(Long userId, SpendingBudgetRequest request) {
        User user = findUser(userId);
        SpendingBudget budget = budgetRepository.findByUserId(userId)
                .orElse(null);

        if (budget == null) {
            budgetRepository.save(SpendingBudget.builder()
                    .user(user)
                    .monthlyBudget(request.monthlyBudget())
                    .build());
        } else {
            budget.update(request.monthlyBudget());
        }
    }

    public SpendingBudgetStatusResponse getBudgetStatus(Long userId) {
        SpendingBudget budget = budgetRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND));

        String currentMonth = YearMonth.now().toString();
        SpendingDashboardResponse dashboard = getDashboard(userId, currentMonth);
        BigDecimal spent = dashboard.totalAmount() != null ? dashboard.totalAmount() : BigDecimal.ZERO;

        return SpendingBudgetStatusResponse.of(budget.getMonthlyBudget(), spent);
    }

    // ── AI 인사이트 (stub) ────────────────────────────────────────────────────

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public SpendingAiInsightResponse getAiInsight(Long userId) {
        YearMonth month = YearMonth.now(java.time.ZoneOffset.UTC);
        var current = getDashboard(userId, month.toString());
        var previous = getDashboard(userId, month.minusMonths(1).toString());
        String facts = "이번 달 기록된 소비는 " + current.totalAmount().toPlainString()
                + "원이며, 지난달 기록된 소비는 " + previous.totalAmount().toPlainString() + "원입니다. ";
        String advice = "등록한 내역이 빠짐없는지 확인하고 카테고리별 지출을 점검해 보세요.";
        String model = "rule-based-v1";
        if (current.totalAmount().signum() > 0 && !aiClient.isStubMode()) {
            try {
                var response = aiClient.insight(new com.youthletter.domain.news.client.dto.AiInsightRequest(
                        month.toString(), current.totalAmount(), previous.totalAmount(), current.categoryTotals()));
                if (response.advice() != null && !response.advice().isBlank() && response.advice().length() <= 600
                        && !response.advice().matches("(?s).*\\d.*")
                        && response.modelVersion() != null && !response.modelVersion().isBlank()) {
                    advice = response.advice();
                    model = response.modelVersion();
                }
            } catch (com.youthletter.global.exception.BusinessException ignored) {
                // 수치는 유지하고 규칙 기반 안내만 반환한다.
            }
        }
        return new SpendingAiInsightResponse(month.atDay(1), LocalDate.now(java.time.ZoneOffset.UTC), facts + advice, model);
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
