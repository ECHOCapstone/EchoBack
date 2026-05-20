package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;

import java.util.ArrayList;
import java.util.List;

/**
 * LlmClient 호출 시 전달되는 컨텍스트 캐리어.
 *
 * <p>모든 필드는 null-safe 보장:
 * <ul>
 *   <li>{@code targetText} 는 null 이면 빈 문자열로 정규화.</li>
 *   <li>리스트 필드는 null 이면 빈 리스트, 그 외에는 방어 복사된 불변 리스트.</li>
 *   <li>{@code weakPhoneme} 만 nullable (특정 음소를 강조하지 않는 호출 케이스 허용).</li>
 * </ul>
 *
 * <p>{@code g2pWords} 는 G2P 가 반환한 단어별 음소 분할이다. rule-based 폴백이
 * {@code canonicalIndex → wordIndex} 매핑을 단어 경계 기준으로 산출할 때 사용한다.
 */
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

    public static final class Builder {
        private String targetText;
        private List<String> perceived;
        private List<String> canonical;
        private List<AnalyzeError> errors;
        private List<G2pWord> g2pWords;
        private String weakPhoneme;

        private Builder() {
        }

        public Builder targetText(String targetText) {
            this.targetText = targetText;
            return this;
        }

        public Builder perceived(List<String> perceived) {
            this.perceived = perceived == null ? null : new ArrayList<>(perceived);
            return this;
        }

        public Builder canonical(List<String> canonical) {
            this.canonical = canonical == null ? null : new ArrayList<>(canonical);
            return this;
        }

        public Builder errors(List<AnalyzeError> errors) {
            this.errors = errors == null ? null : new ArrayList<>(errors);
            return this;
        }

        public Builder g2pWords(List<G2pWord> g2pWords) {
            this.g2pWords = g2pWords == null ? null : new ArrayList<>(g2pWords);
            return this;
        }

        public Builder weakPhoneme(String weakPhoneme) {
            this.weakPhoneme = weakPhoneme;
            return this;
        }

        public LlmContext build() {
            return new LlmContext(targetText, perceived, canonical, errors, g2pWords, weakPhoneme);
        }
    }
}
