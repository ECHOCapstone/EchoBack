package com.capstoneecho.echo_back.learning.session.support;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DefaultSentenceSplitter implements SentenceSplitter {

    private static final Pattern DELIMITER = Pattern.compile("(?<=[.!?])\\s+|\\n+");

    @Override
    public List<String> split(String scriptText) {
        if (scriptText == null || scriptText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(DELIMITER.split(scriptText))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
