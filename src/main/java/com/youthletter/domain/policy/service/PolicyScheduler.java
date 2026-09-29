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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * 매일 09:00 — 마감 D-7, D-3, D-1 정책을 북마크한 사용자에게 알림 생성.
     * 알림 생성만 수행한다. 실제 발송 성공 여부는 푸시 전송 계층에서 갱신해야 한다.
     */
    @Scheduled(cron = "${policy.notification-cron:0 0 9 * * *}", zone="UTC")
    @Transactional
    public void sendDeadlineNotifications() {
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        // 여러 서버의 동일 스케줄 실행을 직렬화한다.
        // 정책 알림은 일 단위 소량 작업이므로 하나의 트랜잭션으로 처리한다.
        jdbc.execute("SELECT pg_advisory_xact_lock(20260930)");
        int[] notifyDays = {7, 3, 1};

        for (int daysLeft : notifyDays) {
            LocalDate targetDate = today.plusDays(daysLeft);
            List<?> expiringPolicies = policyRepository.findPoliciesExpiringBetween(targetDate, targetDate);

            expiringPolicies.forEach(policy -> {
                var p = (com.youthletter.domain.policy.entity.Policy) policy;
                bookmarkRepository.findByPolicyId(p.getId()).forEach(bookmark -> {
                    boolean alreadyScheduled = notificationRepository
                            .existsByUserIdAndPolicyIdAndNotifyBeforeDays(bookmark.getUser().getId(), p.getId(), daysLeft);
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
            // FCM 전송 전에는 발송 완료로 표시하지 않는다.
        });

        log.info("[PolicyScheduler] 오늘({}) 처리된 알림: {}건", today, due.size());
    }
}
