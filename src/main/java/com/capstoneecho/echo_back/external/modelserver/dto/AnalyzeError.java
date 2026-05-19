package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 모델 서버 /analyze 응답에서 Levenshtein 정렬의 한 항목 (매칭 / 치환 / 삽입 / 삭제).
// alignment 와 errors 배열의 원소 타입이 같다 (errors 는 alignment 의 오류만 필터링한 부분 집합).
//
//   op              : MATCH / SUB / INS / DEL
//   canonicalIndex  : 정답 음소 시퀀스에서의 위치 (INS 면 null)
//   canonical       : 정답 음소
//   recognizedIndex : 인식 음소 시퀀스에서의 위치 (DEL 이면 null)
//   recognized      : 인식 음소
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyzeError(
        String op,
        Integer canonicalIndex,
        String canonical,
        Integer recognizedIndex,
        String recognized
) {}
