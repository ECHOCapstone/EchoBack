package com.capstoneecho.echo_back.pronunciation.feedback.entity;

// Levenshtein 정렬 결과에서 한 정렬 자리의 오류 유형.
// 정렬 전체 (matches 포함) 가 아니라 errors 부분 집합만 저장하므로 매칭은 enum 에 포함하지 않는다.
//   SUB : substitution (다른 음소로 인식)
//   DEL : deletion     (정답 음소가 인식되지 않음)
//   INS : insertion    (정답에 없는 음소가 추가로 인식됨)
public enum PhonemeOp {
    SUB,
    DEL,
    INS
}
