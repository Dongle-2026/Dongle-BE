package com.youthletter.domain.notification.push;

import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Notification Settings", description = "앱 알림 설정 API")
@RestController
@RequiredArgsConstructor
public class PushController {
    private final PushService pushService;

    @Operation(summary = "테스트 푸시", description = "내 기기로 테스트 알림 1건. 서버 push.enabled=false면 503. devices=등록된 Expo 토큰 수, succeeded=Expo 접수 수")
    @PostMapping("/api/me/push/test")
    public GlobalResponse<PushService.Result> test(@AuthenticationPrincipal CustomUserPrincipal principal) {
        if (!pushService.isEnabled()) throw new BusinessException(ErrorCode.PUSH_DISABLED);
        return GlobalResponse.ok(pushService.send(principal.getUserId(), PushType.TEST, null,
                "돈글돈글 테스트 알림", "푸시 알림이 정상적으로 연결됐어요.", Map.of()));
    }
}
