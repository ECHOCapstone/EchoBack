package com.capstoneecho.echo_back.learning.session.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 사용자가 입력한 대본을 문장 단위로 쪼갠다.
//   - .  ?  !  줄바꿈을 문장 끝으로 본다 (종결 부호 자체는 보존).
//   - sentenceMinLength 보다 짧은 조각은 앞 조각에 합쳐서 너무 잘게 쪼개지지 않게 한다.
//   - 종결 부호가 하나도 없으면 통째로 하나의 문장으로 돌려준다.
@Component
public class SentenceSplitter {

    private final int minLength;

    public SentenceSplitter(AppProperties properties) {
        this.minLength = properties.session().sentenceMinLength();
    }

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

    // 한 호흡으로 읽을 만한 길이가 되도록 짧은 조각을 앞 조각에 붙인다.
    private List<String> mergeShortFragments(List<String> fragments) {
        var merged = new ArrayList<String>();
        for (var fragment : fragments) {
            if (!merged.isEmpty() && fragment.length() < minLength) {
                int lastIdx = merged.size() - 1;
                merged.set(lastIdx, merged.get(lastIdx) + " " + fragment);
            } else {
                merged.add(fragment);
            }
        }
        return merged;
    }
}
