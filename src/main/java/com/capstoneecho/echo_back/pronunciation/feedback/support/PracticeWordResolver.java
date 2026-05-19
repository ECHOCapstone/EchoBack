package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import com.capstoneecho.echo_back.external.llm.LlmFeedbackGenerator;
// 종합 피드백 화면에서 다시 연습할 단어를 고른다. 우선순위는 위에서 아래로:
//   1. 시드 단어  - Script.practiceWord. 챕터가 미리 정해 둔 의도된 단어라 같은 챕터 학습 흐름에서
//                  LLM 이 약점 음소 따라 동떨어진 단어를 추천하지 않도록 가장 먼저 본다.
//   2. LLM 추천   - 사용자 자유 세션처럼 챕터가 없을 때, 약점 음소 + 챕터 컨텍스트로 한 단어 추천.
//   3. 음소 매핑  - LLM 도 비활성/실패면 ARPAbet 자음 → 대표 단어 매핑.
//   4. 기본값     - yaml 의 app.feedback.default-practice-word.
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
                .or(() -> fromScript(script))
                .or(() -> fromLlm(unitTitle, weakPhoneme))
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
