package com.capstoneecho.echo_back.external.modelserver.support;

import com.capstoneecho.echo_back.external.llm.canonical.PhonemeInventory;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 모델 서버 응답이 표준 CMU ARPABET 음소 목록 밖의 토큰을 출력하는지 감시하고,
// 발견된 비표준 음소를 한 줄 WARN 로그로 흘려보낸다. PhonemeNormalizer 가 분해하지 못한 새 케이스를
// 빠르게 발견해 사전을 확장할 수 있게 한다 — "어떤 mismatch 가 더 있는지 모두 보고 싶다" 는 운영 요구를 충족.
//
// 검사 시점: 호출자 (RecordingService / ChallengeAttemptService 등) 가 canonical 을 확보하고 perceived 를
// PhonemeNormalizer 로 sanitize 한 직후, 정렬에 넘기기 직전. canonical / perceived 둘 다 검사해
// 어느 쪽에서 비표준 토큰이 나왔는지 함께 노출한다.
//
// 표준 음소 집합은 PhonemeInventory 가 SSOT — content/phoneme-inventory.json 한 곳에서만 정의한다.
@Component
public class PhonemeMismatchInspector {

    private static final Logger log = LoggerFactory.getLogger(PhonemeMismatchInspector.class);

    // 분석 정렬에서는 무시되지만 비표준으로 잡지 않을 보조 토큰. inventory 가 SIL/SP 류를 포함하지 않을
    // 경우에도 안전망 역할을 한다. 빈 문자열은 normalize 단계에서 들어올 수 있다.
    private static final Set<String> SILENCE_TOKENS = Set.of("SIL", "SP", "SPN", "");

    private final Set<String> standardPhonemes;

    public PhonemeMismatchInspector(PhonemeInventory inventory) {
        Set<String> merged = new LinkedHashSet<>(inventory.codes());
        merged.addAll(SILENCE_TOKENS);
        this.standardPhonemes = Set.copyOf(merged);
    }

    public void inspect(List<String> canonical, List<String> perceivedNormalized) {
        Set<String> canonicalUnknown = unknownTokens(canonical);
        Set<String> perceivedUnknown = unknownTokens(perceivedNormalized);
        if (canonicalUnknown.isEmpty() && perceivedUnknown.isEmpty()) {
            return;
        }
        // 한 번의 mismatch 마다 한 줄. 한쪽이라도 비표준이면 양쪽 시퀀스를 같이 남겨 추후 사전 추가 결정에 쓰인다.
        log.warn("[PHONEME-MISMATCH] canonical_unknown={} perceived_unknown={} canonical={} perceived={}",
                canonicalUnknown, perceivedUnknown, canonical, perceivedNormalized);
    }

    // 표준 음소 집합에 속하지 않는 토큰만 모은다. 대소문자 / 좌우 공백 / null 은 흡수한다.
    private Set<String> unknownTokens(Collection<String> phonemes) {
        if (phonemes == null || phonemes.isEmpty()) {
            return Set.of();
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String raw : phonemes) {
            if (raw == null) continue;
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            if (!standardPhonemes.contains(token)) {
                unknown.add(raw);
            }
        }
        return unknown;
    }
}
