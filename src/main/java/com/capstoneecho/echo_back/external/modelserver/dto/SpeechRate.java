package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

// 모델 서버가 분류한 발화 속도. 응답 누락 / 알 수 없는 값은 NORMAL 로 폴백한다.
public enum SpeechRate {
    FAST,
    NORMAL,
    SLOW;

    // 모델 서버는 소문자 ("fast" / "normal" / "slow") 로 보낸다.
    @JsonCreator
    public static SpeechRate fromString(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return SpeechRate.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return NORMAL;
        }
    }
}
