package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 /g2p 응답.
//   phonemes : 공백으로 이어 붙인 전체 음소 시퀀스. /analyze 의 canonical 인자와 동일 포맷.
//   words    : 원문 단어 단위 분할.
@JsonIgnoreProperties(ignoreUnknown = true)
public record G2pResult(String phonemes, List<G2pWord> words) {}
