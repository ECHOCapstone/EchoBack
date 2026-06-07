package com.capstoneecho.echo_back.translation.dto;

import java.util.List;

// 요청 입력 순서를 그대로 유지한 번역 결과 묶음. items.size() == request.texts().size().
//   source : 원문 (요청 본문에서 받은 그대로 — 공백 다듬기만 적용).
//   target : 한국어 번역. 번역 실패 또는 provider 비활성 시 빈 문자열.
public record TranslationResponse(List<Item> items) {

    public record Item(String source, String target) {}
}
