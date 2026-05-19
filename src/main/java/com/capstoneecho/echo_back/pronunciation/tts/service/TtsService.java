package com.capstoneecho.echo_back.pronunciation.tts.service;

// 텍스트를 음성 바이트로 합성하는 도메인 추상화. 구현체가 모델 서버 / 외부 TTS API 등을 결정한다.
public interface TtsService {

    // 입력 텍스트와 언어 코드를 받아 합성된 오디오 바이트(audio/mpeg) 를 돌려준다.
    // lang 이 null/blank 면 구현체의 기본 언어가 사용된다.
    byte[] synthesize(String text, String lang);
}
