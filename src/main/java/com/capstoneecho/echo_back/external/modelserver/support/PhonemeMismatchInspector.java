package com.capstoneecho.echo_back.external.modelserver.support;

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
// 검사 시점: ModelServerClient.analyze 가 정규화 + 정렬을 마친 직후. canonical / perceived 둘 다 검사해
// g2p 측 / ASR 측 어느 쪽에서 비표준이 발생했는지 함께 노출한다.
@Component
public class PhonemeMismatchInspector {

    private static final Logger log = LoggerFactory.getLogger(PhonemeMismatchInspector.class);

    // CMU 사전 기반 ARPABET 표준 음소 (스트레스 표기 없는 기본형). 모음 15 + 자음 24 + 침묵.
    // 모델 서버 측이 출력할 수 있는 토큰이지만 채점/정렬에서 무시되는 sil/sp 도 표준으로 인정한다.
    private static final Set<String> STANDARD_PHONEMES = Set.of(
            // monophthongs + diphthongs
            "AA", "AE", "AH", "AO", "AW", "AY", "EH", "ER", "EY",
            "IH", "IY", "OW", "OY", "UH", "UW",
            // consonants
            "B", "CH", "D", "DH", "F", "G", "HH", "JH", "K", "L",
            "M", "N", "NG", "P", "R", "S", "SH", "T", "TH", "V",
            "W", "Y", "Z", "ZH",
            // silence markers — 분석 정렬에서는 무시되지만 비표준으로 잡지 않는다
            "SIL", "SP", "SPN", ""
    );

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
    private static Set<String> unknownTokens(Collection<String> phonemes) {
        if (phonemes == null || phonemes.isEmpty()) {
            return Set.of();
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String raw : phonemes) {
            if (raw == null) continue;
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            if (!STANDARD_PHONEMES.contains(token)) {
                unknown.add(raw);
            }
        }
        return unknown;
    }
}
