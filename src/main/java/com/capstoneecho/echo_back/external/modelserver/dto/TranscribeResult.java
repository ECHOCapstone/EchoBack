package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 POST /transcribe 응답을 백엔드 내부에서 다루는 형태로 정규화한 결과.
//   perceived          : 정규화 (PhonemeNormalizer) 적용된 인식 음소 시퀀스.
//   peakSoftmax        : perceived 와 같은 길이의 프레임별 신뢰도.
//   durationSec        : 오디오 길이 (초).
//   speechRate         : fast / normal / slow 분류 (응답 누락 시 NORMAL).
//   speechRateRatio    : 실제 발화 속도 / 기준 발화 속도 비율 (1.0 = 표준 속도).
//   modelId / modelType: 어드민 / 운영 모니터링용. 어떤 음소인식 모델이 응답을 만들었는지.
@JsonIgnoreProperties(ignoreUnknown = true)
public record TranscribeResult(
        List<String> perceived,
        @JsonAlias("peak_softmax") List<Double> peakSoftmax,
        @JsonAlias("duration_sec") Double durationSec,
        @JsonAlias("speech_rate") SpeechRate speechRate,
        @JsonAlias("speech_rate_ratio") Double speechRateRatio,
        @JsonAlias("model_id") String modelId,
        @JsonAlias("model_type") String modelType
) {
    public TranscribeResult {
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        peakSoftmax = peakSoftmax == null ? List.of() : List.copyOf(peakSoftmax);
        if (speechRate == null) {
            speechRate = SpeechRate.NORMAL;
        }
    }

    public double safeDurationSec() {
        return durationSec == null ? 0.0 : durationSec;
    }
}
