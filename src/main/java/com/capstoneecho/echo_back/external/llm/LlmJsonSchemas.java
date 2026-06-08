package com.capstoneecho.echo_back.external.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Gemini structured output 으로 보낼 JSON Schema 들을 단일 출처에서 관리한다.
// Gemini 2.5+ 는 properties 정의 순서대로 출력하므로 LinkedHashMap 으로 순서를 명시한다.
//
// canonical 자체는 별도 호출 (CanonicalSchema) 이 생성한다 — 본 schema 들은 채점/피드백 응답만 담당.
public final class LlmJsonSchemas {

    private static final List<String> ERROR_TYPE_VALUES = List.of("MATCH", "SUBSTITUTION", "INSERTION", "DELETION");
    private static final List<String> NON_MATCH_ERROR_VALUES = List.of("SUBSTITUTION", "INSERTION", "DELETION");

    private LlmJsonSchemas() {}

    public static Map<String, Object> stepFeedback() {
        Map<String, Object> wrongWord = object(
                ordered(
                        entry("word", typed("string", "원문에서 발견된 약점 단어 자체")),
                        entry("index", typed("integer", "공백 split 한 단어 배열에서의 0-based 인덱스"))),
                List.of("word", "index"));

        Map<String, Object> phonemeTip = object(
                ordered(
                        entry("phoneme", typed("string", "약점 음소의 ARPAbet 표기")),
                        entry("koreanCue", typed("string", "한국식 발음 단서 (한글 음차)")),
                        entry("tip", typed("string", "입 모양 / 혀 위치 등 추가 발음 요령"))),
                List.of("phoneme", "koreanCue", "tip"));

        return object(
                ordered(
                        entry("alignment", array(alignmentOpItem(),
                                "canonical ↔ perceived 정렬 시퀀스 (MATCH 포함)")),
                        entry("errors", array(phonemeErrorItem(),
                                "alignment 에서 추출한 비-MATCH 항목들")),
                        entry("retryRecommended", typed("boolean", "재학습 권고 여부")),
                        entry("guidanceKr", typed("string", "한국어 1~2 문장 피드백")),
                        entry("pronunciationGuide", pronunciationGuide()),
                        entry("strengths", array(typed("string", null), "잘한 점 1~3개")),
                        entry("weaknesses", array(typed("string", null), "보완할 점 1~3개")),
                        entry("wrongWords", array(wrongWord, "약점이 두드러진 단어 목록")),
                        entry("phonemeTips", array(phonemeTip, "약점 음소별 한국식 단서 1~3개"))),
                List.of("alignment", "errors", "retryRecommended",
                        "guidanceKr", "pronunciationGuide"));
    }

    public static Map<String, Object> retryFeedback() {
        Map<String, Object> phonemeTip = object(
                ordered(
                        entry("phoneme", typed("string", "약점 음소")),
                        entry("koreanCue", typed("string", "한국식 발음 단서")),
                        entry("tip", typed("string", "추가 발음 요령"))),
                List.of("phoneme", "koreanCue", "tip"));

        return object(
                ordered(
                        entry("alignment", array(alignmentOpItem(),
                                "canonical ↔ perceived 정렬 시퀀스 (MATCH 포함)")),
                        entry("errors", array(phonemeErrorItem(),
                                "alignment 에서 추출한 비-MATCH 항목들")),
                        entry("correct", typed("boolean", "정답 음소 시퀀스 일치 여부 (정성 판정)")),
                        entry("retryRecommended", typed("boolean", "추가 재시도 권고 여부")),
                        entry("guidanceKr", typed("string", "한국어 1~2 문장 피드백")),
                        entry("pronunciationGuide", pronunciationGuide()),
                        entry("phonemeTips", array(phonemeTip, "약점 음소별 단서"))),
                List.of("alignment", "errors", "correct", "retryRecommended",
                        "guidanceKr", "pronunciationGuide"));
    }

    public static Map<String, Object> comprehensiveFeedback() {
        Map<String, Object> practiceItem = object(
                ordered(
                        entry("text", typed("string", "학습할 영어 텍스트")),
                        entry("kind", enumValue(List.of("WORD", "PHRASE", "SENTENCE"),
                                "단어 / 구 / 문장 구분")),
                        entry("reason", typed("string", "추천 사유 한국어 한 줄"))),
                List.of("text", "kind", "reason"));

        return object(
                ordered(
                        entry("overallScore", typedRange("integer", "챕터 종합 점수 0~100", 0, 100)),
                        entry("summaryKr", typed("string", "한국어 2~3 문장 총평")),
                        entry("strengths", array(typed("string", null), "강점 2~4개")),
                        entry("weaknesses", array(typed("string", null), "약점 2~4개")),
                        entry("nextPracticeItems", array(practiceItem,
                                "다음 학습 단어 / 구 / 문장 3~5개 혼합 추천"))),
                List.of("overallScore", "summaryKr", "nextPracticeItems"));
    }

    private static Map<String, Object> alignmentOpItem() {
        return object(
                ordered(
                        entry("errorType", enumValue(ERROR_TYPE_VALUES,
                                "MATCH = 일치, SUBSTITUTION/INSERTION/DELETION = 비-MATCH")),
                        entry("canonical", typed("string",
                                "정답 음소 (INSERTION 이면 빈 문자열)")),
                        entry("perceived", typed("string",
                                "인식 음소 (DELETION 이면 빈 문자열)")),
                        entry("canonicalIndex", typed("integer",
                                "canonical 시퀀스 내 0-based 인덱스 (INSERTION 이면 -1)"))),
                List.of("errorType"));
    }

    private static Map<String, Object> phonemeErrorItem() {
        return object(
                ordered(
                        entry("op", enumValue(NON_MATCH_ERROR_VALUES,
                                "SUBSTITUTION / INSERTION / DELETION")),
                        entry("canonical", typed("string", "정답 음소 (INSERTION 이면 빈 문자열)")),
                        entry("perceived", typed("string", "인식 음소 (DELETION 이면 빈 문자열)")),
                        entry("canonicalIndex", typed("integer",
                                "canonical 시퀀스 내 0-based 인덱스 (INSERTION 이면 -1)"))),
                List.of("op"));
    }

    private static Map<String, Object> pronunciationGuide() {
        return object(
                ordered(
                        entry("correctPronunciation", typed("string",
                                "이렇게 발음해요 — 정확한 발음 표기 (아학편 한글 음차 또는 영문 syllable)")),
                        entry("perceivedPronunciation", typed("string",
                                "이렇게 말하신 것 같아요 — 학습자의 실제 발음 표기")),
                        entry("explanation", typed("string",
                                "해당 음소가 한국어와 어떻게 다른지 1~2 문장 설명")),
                        entry("correctionTip", typed("string",
                                "교정 제안 — 'X 대신 Y 로 발음해 보세요' 형식"))),
                List.of("correctPronunciation", "perceivedPronunciation", "explanation", "correctionTip"));
    }

    private static Map<String, Object> typed(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        if (description != null) {
            m.put("description", description);
        }
        return m;
    }

    private static Map<String, Object> typedRange(String type, String description, int min, int max) {
        Map<String, Object> m = typed(type, description);
        m.put("minimum", min);
        m.put("maximum", max);
        return m;
    }

    private static Map<String, Object> enumValue(List<String> values, String description) {
        Map<String, Object> m = typed("string", description);
        m.put("enum", values);
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

    private static Map.Entry<String, Map<String, Object>> entry(String key, Map<String, Object> schema) {
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
