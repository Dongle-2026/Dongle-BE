package com.youthletter.domain.notification.controller;
import com.youthletter.domain.notification.service.DeviceTokenService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/users/me/devices")
@RequiredArgsConstructor
public class DeviceTokenController {
    private final DeviceTokenService service;
    public record Request(@NotBlank @Size(max=100) String deviceId,
                          @NotBlank @Size(max=2048) String token,
                          @NotBlank @Pattern(regexp="IOS|ANDROID") String platform) {}
    @PutMapping
    public GlobalResponse<Void> register(@AuthenticationPrincipal CustomUserPrincipal principal, @Valid @RequestBody Request request) {
        service.register(principal.getUserId(), request.deviceId(), request.token(), request.platform());
        return GlobalResponse.ok();
    }
    @DeleteMapping("/{deviceId}")
    public GlobalResponse<Void> remove(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable String deviceId) {
        service.remove(principal.getUserId(), deviceId);
        return GlobalResponse.ok();
    }
}
