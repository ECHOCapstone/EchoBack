package com.capstoneecho.echo_back.member.entity;

// OAuth2 / 소셜 로그인 provider 식별자. 신규 provider 추가시 여기에 값을 더하고
// SocialAccount.(provider, providerUid) 유니크 제약을 그대로 재사용한다.
public enum Provider {
    GOOGLE
}
