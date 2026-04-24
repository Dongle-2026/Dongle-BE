package com.youthletter.global.auth.oauth;

import com.youthletter.domain.user.entity.OAuthAccount;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.OAuthAccountRepository;
import com.youthletter.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String provider = registrationId.toUpperCase();

        OAuth2UserInfo userInfo = extractUserInfo(registrationId, oAuth2User.getAttributes());

        User user = processOAuthUser(provider, userInfo);

        // OAuth2User에 userId/email/role을 포함시켜서 SuccessHandler에서 꺼내 쓸 수 있도록
        Map<String, Object> attributes = Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "name", user.getName(),
                "role",  user.getRole() != null ? user.getRole() : "USER"
        );

        return new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );
    }

    private OAuth2UserInfo extractUserInfo(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> new KakaoUserInfo(attributes);
            case "google" -> new GoogleUserInfo(attributes);
            default -> throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 제공자입니다: " + registrationId);
        };
    }

    private User processOAuthUser(String provider, OAuth2UserInfo userInfo) {
        String providerId = userInfo.getProviderId();

        // 1. 기존 OAuthAccount가 있으면 해당 User 반환
        Optional<OAuthAccount> existingAccount =
                oAuthAccountRepository.findByProviderAndProviderId(provider, providerId);

        if (existingAccount.isPresent()) {
            log.info("기존 OAuth 사용자 로그인: provider={}, providerId={}", provider, providerId);
            return existingAccount.get().getUser();
        }

        // 2. 이메일로 기존 User 조회 (다른 OAuth로 이미 가입한 경우 → 계정 연동)
        String email = userInfo.getEmail();
        User user = (email != null) ? userRepository.findByEmail(email).orElse(null) : null;

        if (user == null) {
            // 3. 완전 신규 사용자 → User 생성
            String name = userInfo.getName() != null ? userInfo.getName() : "유스레터 회원";
            String nickname = generateUniqueNickname(name);

            user = User.builder()
                    .email(email != null ? email : provider.toLowerCase() + "_" + providerId + "@oauth.local")
                    .name(name)
                    .nickname(nickname)
                    .build();
            user = userRepository.save(user);
            log.info("신규 OAuth 사용자 생성: email={}, provider={}", user.getEmail(), provider);
        } else {
            log.info("기존 이메일 사용자에 OAuth 계정 연동: email={}, provider={}", email, provider);
        }

        // 4. OAuthAccount 생성 및 연결
        OAuthAccount oAuthAccount = OAuthAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(providerId)
                .build();
        oAuthAccountRepository.save(oAuthAccount);

        return user;
    }

    private String generateUniqueNickname(String baseName) {
        String nickname = baseName;
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
            nickname = baseName + "_" + UUID.randomUUID().toString().substring(0, 6);
        }
        return baseName + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}