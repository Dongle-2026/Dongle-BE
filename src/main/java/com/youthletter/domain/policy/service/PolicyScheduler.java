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
    private final com.youthletter.domain.notification.push.PushService pushService;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    /**
     * 매일 09:00 — 마감 D-7, D-3, D-1 정책을 북마크한 사용자에게 알림 생성.
     * 알림 생성만 수행한다. 실제 발송 성공 여부는 푸시 전송 계층에서 갱신해야 한다.
     */
    @Scheduled(cron = "${policy.notification-cron:0 0 9 * * *}", zone="UTC")
    public void sendDeadlineNotifications() {
        // 알림 생성은 한 트랜잭션, 푸시 HTTP 호출은 트랜잭션 밖에서 한다.
        List<Due> due = transactions.execute(status -> scheduleAndCollectDue());
        if (due == null) return;
        int sent = 0;
        for (Due d : due) {
            var result = pushService.send(d.userId(), com.youthletter.domain.notification.push.PushType.POLICY_DEADLINE,
                    "policy-notification:" + d.notificationId(), "정책 마감 D-" + d.daysLeft(),
                    "저장한 정책 「" + d.policyTitle() + "」 신청 마감이 다가와요.", java.util.Map.of("policyId", d.policyId()));
            // 푸시가 1대 이상 접수된 경우에만 발송 완료로 표시한다(push.enabled=false면 미발송 유지).
            if (result.succeeded() > 0) {
                jdbc.update("UPDATE policy_notifications SET is_sent = true, notified_at = now() WHERE id = ?", d.notificationId());
                sent++;
            }
        }
        log.info("[PolicyScheduler] 발송 대상 {}건, 푸시 접수 {}건", due.size(), sent);
    }

    record Due(Long notificationId, Long userId, Long policyId, String policyTitle, int daysLeft) {}

    private List<Due> scheduleAndCollectDue() {
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

        return notificationRepository.findUnsentDueNotifications(today).stream()
                .map(n -> new Due(n.getId(), n.getUser().getId(), n.getPolicy().getId(), n.getPolicy().getTitle(), n.getNotifyBeforeDays()))
                .toList();
    }
}
