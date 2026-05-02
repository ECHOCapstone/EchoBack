package com.capstoneecho.echo_back.app.session;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 사용자가 입력한 자유 대본을 학습 단위(문장) 로 쪼개는 정책의 단일 진입점.
//
// 규칙
//   - 마침표(.) / 물음표(?) / 느낌표(!) 가 종결 부호. 종결 부호 자체는 문장 끝에 붙여 보존한다.
//   - 줄바꿈은 종결 부호와 동등하게 취급해 의도된 단락 분리를 존중한다.
//   - 너무 짧은 조각(<MIN_LENGTH 글자) 은 다음 조각과 합쳐 너무 잘게 쪼개지지 않게 한다.
//   - 결과 리스트가 비어 있다면 입력 전체를 하나의 문장으로 돌려준다 (정책 fallback).
@Component
public class SentenceSplitter {

    private static final int MIN_LENGTH = 6;

    public List<String> split(String script) {
        if (script == null) return List.of();
        var trimmed = script.trim();
        if (trimmed.isEmpty()) return List.of();

        var raw = new ArrayList<String>();
        var buffer = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            buffer.append(c);
            if (isTerminator(c)) {
                flush(raw, buffer);
            }
        }
        if (buffer.length() > 0) {
            flush(raw, buffer);
        }

        if (raw.isEmpty()) {
            return List.of(trimmed);
        }
        return mergeShortFragments(raw);
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '?' || c == '!' || c == '\n' || c == '\r';
    }

    private static void flush(List<String> bucket, StringBuilder buffer) {
        var s = buffer.toString().trim();
        if (!s.isEmpty()) bucket.add(s);
        buffer.setLength(0);
    }

    // 짧은 조각을 인접 조각에 흡수시켜 사용자가 한 호흡으로 발음할 만한 길이를 보장한다.
    private static List<String> mergeShortFragments(List<String> fragments) {
        var merged = new ArrayList<String>();
        for (var fragment : fragments) {
            if (!merged.isEmpty() && fragment.length() < MIN_LENGTH) {
                int lastIdx = merged.size() - 1;
                merged.set(lastIdx, merged.get(lastIdx) + " " + fragment);
            } else {
                merged.add(fragment);
            }
        }
        return merged;
    }
}
