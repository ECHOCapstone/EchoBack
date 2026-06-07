package com.capstoneecho.echo_back.external.llm.canonical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Gemini structured output 으로 보낼 canonical 생성 응답 JSON Schema 단일 출처.
public final class CanonicalSchema {

    private CanonicalSchema() {}

    public static Map<String, Object> canonical() {
        Map<String, Object> word = object(
                ordered(
                        entry("word", typed("string", "입력 원문에서 추출한 표면형 (대소문자 / contraction 보존)")),
                        entry("phonemes", array(
                                typed("string", "강세 표기 없는 ARPABET 베이스 코드 (인벤토리 안의 토큰만)"),
                                "단어 한 개의 ARPABET 음소 시퀀스"))),
                List.of("word", "phonemes"));

        return object(
                ordered(
                        entry("words", array(word, "단어 순서대로 정렬된 canonical 항목들"))),
                List.of("words"));
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (description != null) {
            m.put("description", description);
        }
        return m;
    }

    private static Map<String, Object> array(Map<String, Object> items, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        if (description != null) {
            m.put("description", description);
        }
        m.put("items", items);
        return m;
    }

    private static Map<String, Object> object(LinkedHashMap<String, Map<String, Object>> properties,
                                              List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", required);
        return m;
    }

    private static Map.Entry<String, Map<String, Object>> entry(
            String key, Map<String, Object> schema) {
        return Map.entry(key, schema);
    }

    @SafeVarargs
    private static LinkedHashMap<String, Map<String, Object>> ordered(
            Map.Entry<String, Map<String, Object>>... entries) {
        LinkedHashMap<String, Map<String, Object>> m = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : entries) {
            m.put(e.getKey(), e.getValue());
        }
        return m;
    }
}
