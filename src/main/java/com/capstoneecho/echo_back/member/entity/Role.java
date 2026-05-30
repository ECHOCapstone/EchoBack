package com.capstoneecho.echo_back.member.entity;

// 사용자 권한. Spring Security 에는 "ROLE_" 접두사를 붙여 ROLE_USER / ROLE_ADMIN 으로 노출된다.
public enum Role {
    USER,
    ADMIN
}
