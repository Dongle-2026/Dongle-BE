package com.youthletter.domain.user.controller;

import com.youthletter.domain.user.dto.UserProfileResponse;
import com.youthletter.domain.user.service.ProfileService;
import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Tag(name = "Profile", description = "앱 프로필 수정·사진")
@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    /**
     * 텍스트 항목(nickname, bio, avatarId, removeImage)은 쿼리 파라미터·form 필드·multipart 필드 어디로 와도 된다.
     * 사진은 multipart 파일 파트 image로만 받는다.
     */
    @Operation(summary = "프로필 수정",
            description = "nickname, bio, avatarId, removeImage=true는 쿼리/form/multipart 필드 모두 가능. "
                    + "사진은 multipart 파일 파트 image. JSON 본문({nickname,bio,avatarId,removeImage})도 가능. 보내지 않은 항목은 유지")
    @PatchMapping("/api/me/profile")
    public GlobalResponse<UserProfileResponse> update(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                      HttpServletRequest request,
                                                      @RequestParam(required = false) String nickname,
                                                      @RequestParam(required = false) String bio,
                                                      @RequestParam(required = false) String avatarId,
                                                      @RequestParam(required = false) MultipartFile image,
                                                      @RequestParam(defaultValue = "false") boolean removeImage) {
        if (image == null && request.getParameter("image") != null)
            // React Native에서 FormData에 파일 객체가 아닌 값을 넣으면 문자열 필드로 온다.
            throw new BusinessException(ErrorCode.INVALID_IMAGE,
                    "image가 파일이 아니라 텍스트로 왔습니다. FormData에 { uri, name, type } 객체로 넣고 Content-Type 헤더는 직접 지정하지 마세요");
        if (image != null) {
            log.info("프로필 사진 업로드: userId={}, size={}, contentType={}, filename={}",
                    principal.getUserId(), image.getSize(), image.getContentType(), image.getOriginalFilename());
            if (image.isEmpty())
                throw new BusinessException(ErrorCode.INVALID_IMAGE, "image 파일이 비어 있습니다(0바이트). 사진 uri를 확인하세요");
        }
        return GlobalResponse.ok(profileService.update(principal.getUserId(), nickname, bio, avatarId, image, removeImage));
    }

    public record ProfileUpdateJson(String nickname, String bio, String avatarId, Boolean removeImage) {}

    @Operation(summary = "프로필 수정 (JSON)", description = "사진 없이 텍스트 항목만 바꿀 때. 보내지 않은 항목은 유지")
    @PatchMapping(value = "/api/me/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GlobalResponse<UserProfileResponse> updateJson(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                          @RequestBody ProfileUpdateJson body) {
        return GlobalResponse.ok(profileService.update(principal.getUserId(), body.nickname(), body.bio(), body.avatarId(),
                null, Boolean.TRUE.equals(body.removeImage())));
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
