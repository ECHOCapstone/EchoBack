package com.capstoneecho.echo_back.external.llm.translation;

import java.util.List;

// 외부 번역 어댑터. 여러 원문을 한 번에 받아 입력 순서와 같은 길이의 번역 결과를 돌려준다.
// 실패 또는 비활성 provider 의 경우 구현은 빈 문자열 N 개를 반환해 호출 측이 곧장 폴백 흐름으로 들어갈 수 있게 한다.
public interface TranslationClient {

    List<String> translate(List<String> sourceTexts);
}
