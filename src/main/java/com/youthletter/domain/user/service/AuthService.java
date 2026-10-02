package com.youthletter.domain.user.service;

import com.youthletter.domain.user.dto.LoginRequest;
import com.youthletter.domain.user.dto.SignupRequest;
import com.youthletter.domain.user.dto.TokenResponse;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.youthletter.domain.notification.repository.DeviceTokenRepository devices;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .nickname(request.nickname())
                .role("USER")
                .build();
        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken, user.getNickname());
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다");
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!jwtTokenProvider.validateRefreshToken(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "만료되었거나 유효하지 않은 Refresh Token입니다");
        }
        String newAccessToken  = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(newAccessToken, newRefreshToken, user.getNickname());
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.OAUTH_ONLY_ACCOUNT);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        return new TokenResponse(accessToken, refreshToken, user.getNickname());
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        devices.deleteByUserId(userId);
        if (accessToken != null) {
            jwtTokenProvider.blacklistAccessToken(accessToken);
        }
        jwtTokenProvider.deleteRefreshToken(userId);
    }

    @Transactional
    public void withdraw(Long userId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.softDelete();
        devices.deleteByUserId(userId);
        if (accessToken != null) {
            jwtTokenProvider.blacklistAccessToken(accessToken);
        }
        jwtTokenProvider.deleteRefreshToken(userId);
    }
}
