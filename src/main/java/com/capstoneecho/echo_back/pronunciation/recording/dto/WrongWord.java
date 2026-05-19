package com.capstoneecho.echo_back.pronunciation.recording.dto;

// LLM 이 step 응답에서 짚어 준 잘못 발음된 단어 한 항목.
//   word  - 원문 영어 단어 (소문자, 따옴표/구두점 제거)
//   index - targetText 를 공백·구두점으로 쪼개 추출한 영어 단어들 중 0-based 위치
//
// 같은 단어가 여러 번 등장해도 정확한 위치 한 곳만 색칠하기 위해 word 와 index 를 함께 받는다.
public record WrongWord(String word, int index) {}
