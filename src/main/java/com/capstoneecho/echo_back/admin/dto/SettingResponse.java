package com.capstoneecho.echo_back.admin.dto;

// 편집 가능한 설정 한 건. value 는 현재 적용값, type 은 입력 형식(INT/DOUBLE/STRING),
// overridden 이 true 면 yaml 기본값이 아닌 재정의 값이다.
public record SettingResponse(String key, String value, String type, boolean overridden) {}
