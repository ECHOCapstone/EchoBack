package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.AppProperties;
import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// 종합 피드백 화면에서 다시 연습할 단어를 고른다. 우선순위는 위에서 아래로:
//   1. LLM 추천   - 약점 음소가 식별됐을 때 LlmFeedbackGenerator 가 단어 한 개 골라 줌
//   2. 시드 단어  - Script.practiceWord (시드 챕터가 미리 정해 둔 단어)
//   3. 음소 매핑  - 약점 음소만으로 결정해야 할 때의 ARPAbet 자음 → 단어 매핑
//   4. 기본값     - yaml 의 app.feedback.default-practice-word
//
// LLM 이 빈 문자열을 돌려주거나 비활성화돼 있으면 자연스럽게 다음 단계로 떨어진다.
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

    private final LlmFeedbackGenerator llm;
    private final String defaultWord;

    public PracticeWordResolver(LlmFeedbackGenerator llm, AppProperties properties) {
        this.llm = llm;
        this.defaultWord = properties.feedback().defaultPracticeWord();
    }

    public String resolve(Script script, String weakPhoneme, String unitTitle) {
        return Optional.<String>empty()
                .or(() -> fromLlm(unitTitle, weakPhoneme))
                .or(() -> fromScript(script))
                .or(() -> fromPhoneme(weakPhoneme))
                .orElse(defaultWord);
    }

    private Optional<String> fromLlm(String unitTitle, String weakPhoneme) {
        var word = llm.recommendPracticeWord(unitTitle, weakPhoneme);
        return (word != null && !word.isBlank()) ? Optional.of(word) : Optional.empty();
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
