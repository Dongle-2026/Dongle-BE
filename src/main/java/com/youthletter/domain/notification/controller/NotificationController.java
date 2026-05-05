package com.youthletter.domain.notification.controller;

import com.youthletter.domain.notification.dto.NotificationResponse;
import com.youthletter.domain.notification.service.NotificationService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록", description = "미발송(안 읽은) 알림 우선, 최신순")
    @GetMapping
    public GlobalResponse<List<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(notificationService.getMyNotifications(principal.getUserId()));
    }

    @Operation(summary = "단건 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public GlobalResponse<Void> markAsRead(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(principal.getUserId(), notificationId);
        return GlobalResponse.ok("읽음 처리되었습니다", null);
    }

    @Operation(summary = "전체 읽음 처리")
    @PatchMapping("/read-all")
    public GlobalResponse<Integer> markAllAsRead(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        int count = notificationService.markAllAsRead(principal.getUserId());
        return GlobalResponse.ok(count + "건 읽음 처리되었습니다", count);
    }
}
