package com.youthletter.global.auth.oauth;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getName();

    /** 제공자가 이메일 소유를 확인했는지. 확인되지 않은 이메일로는 기존 계정과 비교·저장하지 않는다. */
    boolean isEmailVerified();
}