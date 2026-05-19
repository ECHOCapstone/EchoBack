package com.capstoneecho.echo_back.app.auth.dto;

// 아이디/이메일 중복 확인 응답. true 면 가입 가능.
public record CheckResponse(boolean available) {}
