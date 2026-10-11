package com.youthletter.domain.user.controller;

import com.youthletter.domain.user.dto.UserProfileResponse;
import com.youthletter.domain.user.service.ProfileService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@Tag(name = "Profile", description = "앱 프로필 수정·사진")
@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @Operation(summary = "프로필 수정 (multipart)",
            description = "nickname, bio, avatarId, image(파일), removeImage=true. 보내지 않은 항목은 유지")
    @PatchMapping(value = "/api/me/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GlobalResponse<UserProfileResponse> update(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                      @RequestParam(required = false) String nickname,
                                                      @RequestParam(required = false) String bio,
                                                      @RequestParam(required = false) String avatarId,
                                                      @RequestPart(required = false) MultipartFile image,
                                                      @RequestParam(defaultValue = "false") boolean removeImage) {
        return GlobalResponse.ok(profileService.update(principal.getUserId(), nickname, bio, avatarId, image, removeImage));
    }

    /** 추측 불가능한 키로만 접근. 업로드마다 키가 바뀌므로 길게 캐시해도 된다. */
    @Operation(summary = "프로필 사진", description = "프로필 응답의 profileImageUrl")
    @GetMapping("/api/profile-images/{imageKey}")
    public ResponseEntity<byte[]> image(@PathVariable UUID imageKey) {
        ProfileService.ImageContent image = profileService.image(imageKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .eTag(image.key().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
