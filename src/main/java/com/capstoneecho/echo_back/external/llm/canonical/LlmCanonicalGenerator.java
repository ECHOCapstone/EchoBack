package com.capstoneecho.echo_back.external.llm.canonical;

// 영어 문장에서 단어별 ARPABET 음소 시퀀스를 만든다. 실패 시 BusinessException(CANONICAL_GENERATION_FAILED)
// 을 던지고 (silent fallback 금지), 호출 측이 명시적으로 에러를 사용자에게 노출한다.
public interface LlmCanonicalGenerator {

    CanonicalResult generate(String text);
}
