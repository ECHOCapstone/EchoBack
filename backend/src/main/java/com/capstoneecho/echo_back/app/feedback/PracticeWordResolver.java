package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// 챕터 종합 피드백의 재연습 단어를 결정하는 단일 정책. 3-tier fallback 으로 동작한다.
//
//   1) Script.practiceWord  : 어드민/시드가 챕터에 명시한 값. 모든 시드 챕터가 채우는 SSOT.
//   2) 음소 → 단어 매핑       : Script 가 없는 자유 스크립트 (사용자 맞춤) 에서 weakPhoneme 만으로
//                              결정해야 하는 경우의 발음 영역 도메인 지식.
//   3) DEFAULT_WORD          : 어떤 단서도 없을 때의 마지막 기본값.
//
// 챕터 제목 키워드 매칭은 의도적으로 두지 않는다. 시드 챕터는 모두 (1) 에서 결정되고,
// 사용자 자유 스크립트는 chapter title 자체가 의미를 가지지 않으므로 (2)/(3) 만으로 충분하다.
@Component
public class PracticeWordResolver {

    static final String DEFAULT_WORD = "rabbit";

    // ARPAbet 자음 키 → 그 음소를 대표하는 학습 단어. 발음 영역의 도메인 지식이라 코드에 둔다.
    // 시드 챕터들의 practiceWord 와 의도적으로 일치시켜 두 경로의 결과가 같은 단어로 수렴한다.
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

    public String resolve(Script script, String weakPhoneme) {
        return Optional.<String>empty()
                .or(() -> fromScript(script))
                .or(() -> fromPhoneme(weakPhoneme))
                .orElse(DEFAULT_WORD);
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
