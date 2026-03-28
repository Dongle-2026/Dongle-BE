package com.youthletter.global.auth.oauth;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getName();
}