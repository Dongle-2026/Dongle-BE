package com.youthletter.domain.user.service;

import com.youthletter.domain.user.dto.UserProfileResponse;
import com.youthletter.domain.user.entity.ProfileImage;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.ProfileImageRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** 앱 프로필 수정(PATCH /api/me/profile)과 프로필 사진 제공. */
@Service
@RequiredArgsConstructor
public class ProfileService {
    private static final Pattern AVATAR_ID = Pattern.compile("[a-z0-9_-]{1,30}");

    private final UserRepository userRepository;
    private final ProfileImageRepository profileImageRepository;
    private final ProfileImageProcessor imageProcessor;
    private final UserService userService;

    public record ImageContent(byte[] bytes, String contentType, UUID key) {}

    /**
     * null 필드는 변경하지 않는다. image와 removeImage를 함께 보내면 400.
     * 이미지 처리는 검증이 끝난 뒤 같은 트랜잭션에서 저장하므로 실패 시 프로필도 바뀌지 않는다.
     */
    @Transactional
    public UserProfileResponse update(Long userId, String nickname, String bio, String avatarId,
                                      MultipartFile image, boolean removeImage) {
        boolean hasImage = image != null && !image.isEmpty();
        if (hasImage && removeImage) throw new BusinessException(ErrorCode.INVALID_INPUT, "image와 removeImage는 함께 보낼 수 없습니다");
        if (nickname != null) {
            nickname = nickname.strip();
            if (nickname.length() < 2 || nickname.length() > 30)
                throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 2자 이상 30자 이하여야 합니다");
        }
        if (bio != null) {
            bio = bio.strip();
            if (bio.length() > 100) throw new BusinessException(ErrorCode.INVALID_INPUT, "소개글은 100자 이하여야 합니다");
        }
        if (avatarId != null && !AVATAR_ID.matcher(avatarId).matches())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "avatarId 형식이 올바르지 않습니다");
        ProfileImageProcessor.Processed processed = hasImage ? imageProcessor.process(read(image)) : null;

        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (nickname != null && !nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname))
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        user.updateAppProfile(nickname, bio, avatarId);

        if (processed != null) {
            ProfileImage stored = profileImageRepository.findById(userId).orElseGet(() -> new ProfileImage(userId));
            stored.replace(processed.bytes(), processed.contentType(), processed.width(), processed.height());
            profileImageRepository.save(stored);
        } else if (removeImage) {
            profileImageRepository.findById(userId).ifPresent(profileImageRepository::delete);
        }
        profileImageRepository.flush();
        return userService.toProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public ImageContent image(UUID key) {
        ProfileImage image = profileImageRepository.findByImageKey(key)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND));
        return new ImageContent(image.getContent(), image.getContentType(), image.getImageKey());
    }

    private static byte[] read(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
