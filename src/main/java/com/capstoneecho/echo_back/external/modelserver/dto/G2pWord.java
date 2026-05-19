package com.capstoneecho.echo_back.external.modelserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// 모델 서버 /g2p 응답의 단어별 분할. word 는 원문 단어, phonemes 는 그 단어의 음소 시퀀스.
// 단어 단위 강조 UI 에 사용된다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record G2pWord(String word, List<String> phonemes) {}
