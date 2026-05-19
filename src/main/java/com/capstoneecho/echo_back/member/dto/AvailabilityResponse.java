package com.capstoneecho.echo_back.member.dto;

// 아이디/이메일 중복 확인 응답. true 면 가입 가능.
public record AvailabilityResponse(boolean available) {}
