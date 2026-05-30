package com.capstoneecho.echo_back.learning.session.support;

import java.util.List;

public interface SentenceSplitter {

    // 스크립트 텍스트를 문장 단위로 나눈다. 입력이 비면 빈 리스트를 돌린다 — null 리스트나 null 원소는 반환하지 않는다.
    List<String> split(String scriptText);
}
