package com.capstoneecho.echo_back.external.llm.canonical;

// 영어 텍스트를 받아 단어별 ARPABET canonical 시퀀스를 만든다.
// 내부 구현은 모델 서버 /g2p (CMU baseline) + Gemini refine 두 단계로 구성된다.
// 실패 시 BusinessException(CANONICAL_GENERATION_FAILED) — silent fallback 금지.
public interface LlmCanonicalGenerator {

    CanonicalResult generate(String text);
}
