package com.youthletter.global.auth.oauth;

import com.youthletter.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    static final String EMAIL_ALREADY_REGISTERED = "email_already_registered";
    private final SocialAccountService socialAccounts;

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

    /** 계정 연결 규칙은 모바일과 공통(SocialAccountService). 같은 이메일 기존 계정은 거부하고 기존 방법을 안내한다. */
    User processOAuthUser(String provider, OAuth2UserInfo userInfo) {
        try {
            return socialAccounts.resolve(provider, userInfo.getProviderId(), userInfo.getEmail(),
                    userInfo.isEmailVerified(), userInfo.getName()).user();
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED) throw e;
            throw new OAuth2AuthenticationException(new OAuth2Error(EMAIL_ALREADY_REGISTERED, e.getMessage(), null), e.getMessage());
        }
    }
}
