package com.youthletter.domain.notification.service;

import com.youthletter.domain.notification.dto.NotificationResponse;
import com.youthletter.domain.policy.entity.PolicyNotification;
import com.youthletter.domain.policy.repository.PolicyNotificationRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final PolicyNotificationRepository notificationRepository;

    /**
     * 내 알림 목록 (미발송 우선, 최신순)
     */
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderBySentAndCreatedAt(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 단건 읽음 처리 (markAsSent)
     */
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        PolicyNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POLICY_NOT_FOUND));

        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        notification.markAsSent();
    }

    /**
     * 전체 읽음 처리
     */
    @Transactional
    public int markAllAsRead(Long userId) {
        List<PolicyNotification> unread = notificationRepository.findUnsentByUserId(userId);
        unread.forEach(PolicyNotification::markAsSent);
        return unread.size();
    }
}
