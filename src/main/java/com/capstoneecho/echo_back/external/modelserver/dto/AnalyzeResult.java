package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 /analyze 응답 1:1 매핑.
//   perceived   : 인식된 음소 시퀀스
//   canonical   : 정답 음소 시퀀스 (canonical 인자 미전송 시 null)
//   peakSoftmax : 각 perceived 음소의 softmax 최댓값
//   alignment   : Levenshtein 정렬 결과 전체
//   errors      : alignment 에서 오류 항목만 필터링한 부분 집합
//   per         : Phoneme Error Rate (canonical 미전송 시 null)
//   durationSec : 디코딩된 오디오 길이(초)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeResult(
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        List<AnalyzeError> alignment,
        List<AnalyzeError> errors,
        Double per,
        Double durationSec
) {}
