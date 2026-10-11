package com.youthletter.global.auth.mobile;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** application.yml의 mobile-oauth.providers.{google,kakao} */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mobile-oauth")
public class MobileOAuthProperties {
    private Map<String, Provider> providers = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Provider {
        private String jwkSetUri;
        private List<String> issuers = List.of();
        /** 허용할 aud(클라이언트 ID/앱 키). 비어 있으면 해당 제공자 로그인 불가. */
        private List<String> audiences = List.of();
    }
}
