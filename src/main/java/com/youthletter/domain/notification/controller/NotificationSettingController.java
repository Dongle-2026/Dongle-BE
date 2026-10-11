package com.youthletter.domain.notification.controller;

import com.youthletter.domain.notification.dto.NotificationSettingResponse;
import com.youthletter.domain.notification.dto.NotificationSettingUpdateRequest;
import com.youthletter.domain.notification.service.NotificationSettingService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification Settings", description = "앱 알림 설정 API")
@RestController
@RequestMapping("/api/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {
    private final NotificationSettingService service;

    @Operation(summary = "알림 설정 조회", description = "저장 전에는 기본값(모두 true)")
    @GetMapping
    public GlobalResponse<NotificationSettingResponse> get(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return GlobalResponse.ok(service.get(principal.getUserId()));
    }

    @Operation(summary = "알림 설정 변경", description = "보낸 항목만 변경. 응답은 변경 후 전체 설정")
    @PatchMapping
    public GlobalResponse<NotificationSettingResponse> update(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                              @RequestBody NotificationSettingUpdateRequest request) {
        return GlobalResponse.ok(service.update(principal.getUserId(), request));
    }
}
