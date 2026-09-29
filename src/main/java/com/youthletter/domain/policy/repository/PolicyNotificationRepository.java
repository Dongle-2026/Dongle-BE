package com.youthletter.domain.policy.repository;

import com.youthletter.domain.policy.entity.PolicyNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PolicyNotificationRepository extends JpaRepository<PolicyNotification, Long> {

    /**
     * 미발송 알림 중, policy.end_date - today = notify_before_days 인 건 조회.
     * 배치 스케줄러(PolicyScheduler)에서 매일 아침 호출.
     */
    @Query(value = """
            SELECT pn.* FROM policy_notifications pn
            JOIN policies p ON p.id = pn.policy_id
            WHERE pn.is_sent = false
              AND p.end_date IS NOT NULL
              AND (p.end_date - CAST(:today AS date)) = pn.notify_before_days
            """, nativeQuery = true)
    List<PolicyNotification> findUnsentDueNotifications(@Param("today") LocalDate today);

    boolean existsByUserIdAndPolicyId(Long userId, Long policyId);
    boolean existsByUserIdAndPolicyIdAndNotifyBeforeDays(Long userId, Long policyId, Integer days);

    /**
     * 사용자의 알림 목록 (미발송 우선, 최신순)
     */
    @Query(value = """
            SELECT * FROM policy_notifications
            WHERE user_id = :userId
            ORDER BY is_read ASC, created_at DESC
            """, nativeQuery = true)
    List<PolicyNotification> findByUserIdOrderBySentAndCreatedAt(@Param("userId") Long userId);

    /**
     * 미발송 알림 목록 (일괄 읽음 처리용)
     */
    @Query("SELECT n FROM PolicyNotification n WHERE n.user.id = :userId AND n.read = false")
    List<PolicyNotification> findUnsentByUserId(@Param("userId") Long userId);
}
