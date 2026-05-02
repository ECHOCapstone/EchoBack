package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

// 챕터 종합 피드백의 재연습 단어를 결정하는 단일 정책. 4-tier fallback 으로 동작한다.
//
//   1) Script.practiceWord  : 시드/어드민이 챕터에 명시한 값 (DB 데이터, 가장 강한 SSOT)
//   2) 음소 → 단어 매핑       : weakPhoneme 가 식별되면 그 음소의 대표 연습 단어
//   3) 챕터 제목 키워드 매칭 : 음소 매핑도 실패한 경우의 사람 친화적 폴백 (R/L → light 등)
//   4) DEFAULT_WORD          : 어떤 단서도 없을 때의 마지막 기본값
//
// 정책이 한 곳에 모여 있어 챕터 추가 / LLM 교체 / 규칙 변경 시 단일 진입점만 수정하면 된다.
@Component
public class PracticeWordResolver {

    static final String DEFAULT_WORD = "rabbit";

    // ARPAbet 자음 키 → 그 음소를 대표하는 학습 단어. 두 generator 가 공유하던 매핑을 여기로 흡수.
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

    public String resolve(Script script, String weakPhoneme, String unitTitle) {
        return Optional.<String>empty()
                .or(() -> fromScript(script))
                .or(() -> fromPhoneme(weakPhoneme))
                .or(() -> fromTitle(unitTitle))
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

    private Optional<String> fromTitle(String unitTitle) {
        if (unitTitle == null) return Optional.empty();
        var lower = unitTitle.toLowerCase();
        if (lower.contains("r vs l") || lower.contains("r/l")) return Optional.of("light");
        if (lower.contains("v vs b") || lower.contains("v/b")) return Optional.of("vest");
        if (lower.contains("f vs p") || lower.contains("f/p")) return Optional.of("fine");
        if (lower.contains("th") || lower.contains("dh")) return Optional.of("think");
        if (lower.contains("sh") || lower.contains("zh")) return Optional.of("shoes");
        if (lower.contains("잰말") || lower.contains("tongue")) return Optional.of("sheet");
        return Optional.empty();
    }
}
