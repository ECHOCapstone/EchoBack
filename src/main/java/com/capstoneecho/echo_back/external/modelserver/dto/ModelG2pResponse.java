package com.capstoneecho.echo_back.external.modelserver.dto;

import java.util.List;

// 모델 서버 /g2p 응답.
//   phonemes : 공백으로 이어 붙인 전체 음소 시퀀스. /analyze 의 canonical 인자와 동일 포맷.
//   words    : 원문 단어와 그 단어의 음소 목록. 단어 단위 강조 UI 용.
public record ModelG2pResponse(String phonemes, List<Word> words) {

    public record Word(String word, List<String> phonemes) {}
}
