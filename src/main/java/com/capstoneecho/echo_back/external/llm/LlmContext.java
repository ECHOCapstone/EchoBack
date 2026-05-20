package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import java.util.List;

// LlmClient 호출 컨텍스트. 리스트는 null 입력 시 빈 리스트로, targetText 는 빈 문자열로 정규화된다.
// weakPhoneme 만 nullable. g2pWords 는 단어 경계 기준 wrong-word 매핑에 쓰인다.
public record LlmContext(
        String targetText,
        List<String> perceived,
        List<String> canonical,
        List<AnalyzeError> errors,
        List<G2pWord> g2pWords,
        String weakPhoneme
) {

    public LlmContext {
        targetText = targetText == null ? "" : targetText;
        perceived = perceived == null ? List.of() : List.copyOf(perceived);
        canonical = canonical == null ? List.of() : List.copyOf(canonical);
        errors = errors == null ? List.of() : List.copyOf(errors);
        g2pWords = g2pWords == null ? List.of() : List.copyOf(g2pWords);
    }

    public static Builder builder() {
        return new Builder();
    }

    // 부분 채우기를 허용하는 짧은 빌더. record canonical 생성자가 null-safe 정규화를 맡는다.
    public static final class Builder {
        private String targetText;
        private List<String> perceived;
        private List<String> canonical;
        private List<AnalyzeError> errors;
        private List<G2pWord> g2pWords;
        private String weakPhoneme;

        private Builder() {}

        public Builder targetText(String v) { this.targetText = v; return this; }
        public Builder perceived(List<String> v) { this.perceived = v; return this; }
        public Builder canonical(List<String> v) { this.canonical = v; return this; }
        public Builder errors(List<AnalyzeError> v) { this.errors = v; return this; }
        public Builder g2pWords(List<G2pWord> v) { this.g2pWords = v; return this; }
        public Builder weakPhoneme(String v) { this.weakPhoneme = v; return this; }

        public LlmContext build() {
            return new LlmContext(targetText, perceived, canonical, errors, g2pWords, weakPhoneme);
        }
    }
}
