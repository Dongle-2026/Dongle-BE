package com.youthletter.domain.spending.service;

import com.youthletter.domain.spending.dto.SpendingCreateRequest;
import com.youthletter.domain.spending.dto.SpendingDashboardResponse;
import com.youthletter.domain.spending.entity.SpendingEvent;
import com.youthletter.domain.spending.entity.SpendingSnapshot;
import com.youthletter.domain.spending.repository.SpendingEventRepository;
import com.youthletter.domain.spending.repository.SpendingSnapshotRepository;
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
    private final UserRepository userRepository;

    // spending_id 생성용 (실무에서는 시퀀스 또는 Snowflake ID 사용)
    private final AtomicLong spendingIdGenerator = new AtomicLong(System.currentTimeMillis());

    /**
     * 소비 기록 추가 (Event Sourcing: CREATED 이벤트 삽입)
     */
    @Transactional
    public Long createSpending(Long userId, SpendingCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        long spendingId = spendingIdGenerator.incrementAndGet();

        SpendingEvent event = SpendingEvent.builder()
                .user(user)
                .eventType("CREATED")
                .spendingId(spendingId)
                .amount(request.amount())
                .category(request.category())
                .description(request.description())
                .spentAt(request.spentAt())
                .metadata(request.metadata())
                .build();

        eventRepository.save(event);
        return spendingId;
    }

    /**
     * 소비 기록 수정 (Event Sourcing: UPDATED 이벤트 삽입, 기존 이벤트는 그대로)
     */
    @Transactional
    public void updateSpending(Long userId, Long spendingId, SpendingCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        SpendingEvent event = SpendingEvent.builder()
                .user(user)
                .eventType("UPDATED")
                .spendingId(spendingId)
                .amount(request.amount())
                .category(request.category())
                .description(request.description())
                .spentAt(request.spentAt())
                .metadata(request.metadata())
                .build();

        eventRepository.save(event);
    }

    /**
     * 소비 기록 삭제 (Event Sourcing: DELETED 이벤트 삽입)
     */
    @Transactional
    public void deleteSpending(Long userId, Long spendingId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 최신 이벤트를 기준으로 삭제 이벤트 생성
        List<SpendingEvent> events = eventRepository
                .findBySpendingIdOrderByCreatedAtAsc(spendingId);

        if (events.isEmpty()) return;

        SpendingEvent latest = events.get(events.size() - 1);

        SpendingEvent deleteEvent = SpendingEvent.builder()
                .user(user)
                .eventType("DELETED")
                .spendingId(spendingId)
                .amount(latest.getAmount())
                .category(latest.getCategory())
                .description(latest.getDescription())
                .spentAt(latest.getSpentAt())
                .build();

        eventRepository.save(deleteEvent);
    }

    /**
     * 월별 소비 대시보드 조회 (스냅샷 기반 — O(1))
     */
    public SpendingDashboardResponse getDashboard(Long userId, String month) {
        // month: "2026-03" 형식
        SpendingSnapshot snapshot = snapshotRepository
                .findByUserIdAndSnapshotMonth(userId, month)
                .orElse(null);

        if (snapshot != null) {
            return SpendingDashboardResponse.fromSnapshot(snapshot);
        }

        // 스냅샷이 없으면 이벤트에서 직접 집계 (최초 조회 시)
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Object[]> aggregated = eventRepository
                .aggregateByCategory(userId, start, end);

        return SpendingDashboardResponse.fromAggregation(month, aggregated);
    }
}
