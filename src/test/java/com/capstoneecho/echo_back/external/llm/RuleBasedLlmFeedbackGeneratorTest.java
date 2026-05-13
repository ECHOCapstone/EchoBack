package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.pronunciation.recording.dto.WrongWord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedLlmFeedbackGeneratorTest {

    private final RuleBasedLlmFeedbackGenerator generator = new RuleBasedLlmFeedbackGenerator();

    @Test
    @DisplayName("summarizeRecording: errors 없음 → wrongWords 빈 배열 + non-empty guidance")
    void noErrorsYieldsEmptyWrongWordsAndNonEmptyGuidance() {
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .perceived(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of())
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).isEmpty();
        assertThat(result.guidanceKr()).isNotBlank();
    }

    @Test
    @DisplayName("summarizeRecording: errors at canonicalIndex resolve words from targetText")
    void errorsAtCanonicalIndexResolveWordsFromTargetText() {
        // targetText: 2 words, canonical: 8 phonemes (≈4 per word).
        // canonicalIndex=5 → wordIdx = floor(5*2/8) = 1 → "world".
        AnalyzeError err = new AnalyzeError("sub", 5, "EH", "ER");
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .perceived(List.of("HH", "AH", "L", "OW", "W", "EH", "L", "D"))
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of(err))
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("world", 1));
        assertThat(result.guidanceKr()).isNotBlank();
    }

    @Test
    @DisplayName("summarizeRecording: 여러 error 가 같은 단어를 가리키면 중복 제거")
    void duplicateWordHitsAreDeduplicated() {
        AnalyzeError e1 = new AnalyzeError("sub", 1, "EH", "AH");
        AnalyzeError e2 = new AnalyzeError("del", 2, null, "L");
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of(e1, e2))
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("Hello", 0));
    }

    @Test
    @DisplayName("summarizeFeedback / retryGuidance / suggestPracticeWord 는 항상 non-empty 폴백 제공")
    void textGuidancesAreAlwaysNonEmpty() {
        LlmContext empty = LlmContext.builder().build();

        assertThat(generator.summarizeFeedback(empty)).isNotBlank();
        assertThat(generator.retryGuidance(empty)).isNotBlank();
        assertThat(generator.suggestPracticeWord(empty)).isNotBlank();
    }

    @Test
    @DisplayName("suggestPracticeWord 는 targetText 가 있으면 그 첫 단어를 우선한다")
    void suggestPracticeWordPrefersFirstWordOfTargetText() {
        LlmContext context = LlmContext.builder()
                .targetText("Practice makes perfect")
                .build();

        assertThat(generator.suggestPracticeWord(context)).isEqualTo("Practice");
    }
}
