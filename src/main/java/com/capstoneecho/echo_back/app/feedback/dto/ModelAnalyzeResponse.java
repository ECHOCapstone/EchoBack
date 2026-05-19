package com.capstoneecho.echo_back.app.feedback.dto;

import java.util.List;

// 모델 서버 /analyze 응답 1:1 매핑.
//   perceived     : 인식된 음소 시퀀스
//   canonical     : 정답 음소 시퀀스 (canonical 인자 미전송 시 null)
//   peakSoftmax   : 각 perceived 음소의 softmax 최댓값
//   alignment     : Levenshtein 정렬 결과
//   errors        : alignment 중 오류만 필터
//   per           : Phoneme Error Rate (canonical 미전송 시 null)
//   durationSec   : 디코딩된 오디오 길이(초)
public record ModelAnalyzeResponse(
        List<String> perceived,
        List<String> canonical,
        List<Double> peakSoftmax,
        List<AlignmentItem> alignment,
        List<AlignmentItem> errors,
        Double per,
        Double durationSec
) {

    public record AlignmentItem(
            String op,
            Integer canonicalIndex,
            String canonical,
            Integer recognizedIndex,
            String recognized
    ) {}
}
