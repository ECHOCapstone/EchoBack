package com.capstoneecho.echo_back.app.feedback;

// weakPhoneme 으로 재연습 단어를 찾지 못했을 때 챕터 제목으로부터 적절한 단어를 추론하는 SSOT.
// 매핑이 모두 실패하면 DEFAULT_PRACTICE_WORD 로 떨어진다.
//
// rule-based / gemini 양쪽 LlmFeedbackGenerator 가 동일한 폴백을 공유하기 위해 분리되었다.
final class PracticeWordPolicy {

    static final String DEFAULT_PRACTICE_WORD = "rabbit";

    private PracticeWordPolicy() {}

    static String byUnitTitle(String unitTitle) {
        if (unitTitle == null) return DEFAULT_PRACTICE_WORD;
        var lower = unitTitle.toLowerCase();
        if (lower.contains("r vs l") || lower.contains("r/l")) return "light";
        if (lower.contains("v vs b") || lower.contains("v/b")) return "vest";
        if (lower.contains("f vs p") || lower.contains("f/p")) return "fine";
        if (lower.contains("th vs dh") || lower.contains("th") || lower.contains("dh")) return "think";
        if (lower.contains("sh") || lower.contains("zh")) return "shoes";
        if (lower.contains("잰말") || lower.contains("tongue")) return "sheet";
        return DEFAULT_PRACTICE_WORD;
    }
}
