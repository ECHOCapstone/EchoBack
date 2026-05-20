package com.capstoneecho.echo_back.external.modelserver.dto;

import java.util.List;

// 모델 서버 /analyze 응답을 코드 내부에서 사용하는 형태로 정규화한 결과.
// canonical / per 는 응답에 들어오지 않을 수 있는 nullable 필드다.
public record AnalyzeResult(
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        List<Object> alignment,
        List<AnalyzeError> errors,
        Double per,
        double durationSec
) {
    public List<String> canonicalOrEmpty() {
        return canonical == null ? List.of() : canonical;
    }
}
