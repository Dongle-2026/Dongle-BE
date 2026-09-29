package com.youthletter.domain.notification.dto;

import com.youthletter.domain.policy.entity.PolicyNotification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 응답")
public record NotificationResponse(
        @Schema(description = "알림 ID") Long id,
        @Schema(description = "정책 ID") Long policyId,
        @Schema(description = "정책 제목") String policyTitle,
        @Schema(description = "마감 D-{n} 안내") int notifyBeforeDays,
        @Schema(description = "발송 여부") boolean isSent,
        @Schema(description = "사용자 읽음 여부") boolean isRead,
        @Schema(description = "알림 생성일시") LocalDateTime createdAt
) {
    public static NotificationResponse from(PolicyNotification n) {
        return new NotificationResponse(
                n.getId(),
                n.getPolicy().getId(),
                n.getPolicy().getTitle(),
                n.getNotifyBeforeDays(),
                n.getIsSent(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
