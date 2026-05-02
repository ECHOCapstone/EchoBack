package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// 종합 피드백 화면에서 다시 연습할 단어를 고른다.
// 시드 챕터는 Script.practiceWord 가 정답이고, 사용자가 직접 만든 세션처럼 챕터가 없을 때만
// 약점 음소로 단어를 추정한다. 그것도 못하면 yaml 의 기본값.
@Component
public class PracticeWordResolver {

    // ARPAbet 자음 → 대표 연습 단어. 시드 챕터의 practiceWord 와 같은 값을 일부러 맞춰 둬서
    // 두 경로 결과가 같은 단어로 수렴한다.
    private static final Map<String, String> PHONEME_TO_WORD = Map.ofEntries(
            Map.entry("r", "rabbit"),
            Map.entry("l", "light"),
            Map.entry("v", "vest"),
            Map.entry("b", "best"),
            Map.entry("f", "fine"),
            Map.entry("p", "pine"),
            Map.entry("th", "think"),
            Map.entry("dh", "this"),
            Map.entry("sh", "shoes"),
            Map.entry("zh", "measure"),
            Map.entry("ng", "song")
    );

    private final String defaultWord;

    public PracticeWordResolver(AppProperties properties) {
        this.defaultWord = properties.feedback().defaultPracticeWord();
    }

    public String resolve(Script script, String weakPhoneme) {
        return Optional.<String>empty()
                .or(() -> fromScript(script))
                .or(() -> fromPhoneme(weakPhoneme))
                .orElse(defaultWord);
    }

    private Optional<String> fromScript(Script script) {
        if (script == null) return Optional.empty();
        var word = script.getPracticeWord();
        return (word != null && !word.isBlank()) ? Optional.of(word) : Optional.empty();
    }

    private Optional<String> fromPhoneme(String weakPhoneme) {
        if (weakPhoneme == null || weakPhoneme.isBlank()) return Optional.empty();
        return Optional.ofNullable(PHONEME_TO_WORD.get(weakPhoneme.toLowerCase()));
    }
}
