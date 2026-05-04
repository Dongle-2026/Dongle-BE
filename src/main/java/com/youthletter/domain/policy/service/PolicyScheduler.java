package com.youthletter.domain.policy.service;

import com.youthletter.domain.policy.entity.PolicyNotification;
import com.youthletter.domain.policy.repository.PolicyBookmarkRepository;
import com.youthletter.domain.policy.repository.PolicyNotificationRepository;
import com.youthletter.domain.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyScheduler {

    private final PolicyRepository policyRepository;
    private final PolicyBookmarkRepository bookmarkRepository;
    private final PolicyNotificationRepository notificationRepository;

    /**
     * 매일 09:00 — 마감 D-7, D-3, D-1 정책을 북마크한 사용자에게 알림 생성.
     * 중복 발송 방지: is_sent = false인 건만 조회해서 처리 후 markAsSent().
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void sendDeadlineNotifications() {
        LocalDate today = LocalDate.now();
        int[] notifyDays = {7, 3, 1};

        for (int daysLeft : notifyDays) {
            LocalDate targetDate = today.plusDays(daysLeft);
            List<?> expiringPolicies = policyRepository.findPoliciesExpiringBetween(targetDate, targetDate);

            expiringPolicies.forEach(policy -> {
                var p = (com.youthletter.domain.policy.entity.Policy) policy;
                bookmarkRepository.findByPolicyId(p.getId()).forEach(bookmark -> {
                    boolean alreadyScheduled = notificationRepository
                            .existsByUserIdAndPolicyId(bookmark.getUser().getId(), p.getId());
                    if (!alreadyScheduled) {
                        notificationRepository.save(PolicyNotification.builder()
                                .user(bookmark.getUser())
                                .policy(p)
                                .notifyBeforeDays(daysLeft)
                                .build());
                    }
                });
            });
        }

        // 발송 대상 처리 (실제 Push 연동 전까지 로그로 대체)
        List<PolicyNotification> due = notificationRepository.findUnsentDueNotifications(today);
        due.forEach(notification -> {
            log.info("[PolicyScheduler] 알림 발송 대상 — userId={}, policyId={}, D-{}",
                    notification.getUser().getId(),
                    notification.getPolicy().getId(),
                    notification.getNotifyBeforeDays());
            notification.markAsSent();
        });

        log.info("[PolicyScheduler] 오늘({}) 처리된 알림: {}건", today, due.size());
    }
}
