package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import com.capstoneecho.echo_back.global.config.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedLlmFeedbackGeneratorTest {

    private static final AppProperties FIXTURE = new AppProperties(
            null, null, null, null, null, null, null, null,
            new AppProperties.Gamification(10, 7, 3, 5, 7, 70.0, "오늘의 랭킹", "the"),
            new AppProperties.Messages(
                    "발음을 더 또렷하게 따라 읽어 보세요.",
                    "꾸준한 연습이 발음 개선에 도움이 됩니다.",
                    "해당 단어를 한 번 더 천천히 따라 읽어 보세요.",
                    "업로드 가능한 파일 크기를 초과했습니다.",
                    "text 는 비어 있을 수 없습니다."
            )
    );

    private final RuleBasedLlmFeedbackGenerator generator = new RuleBasedLlmFeedbackGenerator(FIXTURE);

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
    @DisplayName("summarizeRecording: errors at canonicalIndex resolve words via real g2p word boundaries")
    void errorsAtCanonicalIndexResolveWordsFromTargetText() {
        // Hello: phonemes[0..3], world: phonemes[4..7]. canonicalIndex=5 falls into word 1 "world".
        AnalyzeError err = new AnalyzeError("sub", 5, "EH", "ER");
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .perceived(List.of("HH", "AH", "L", "OW", "W", "EH", "L", "D"))
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of(err))
                .g2pWords(List.of(
                        new G2pWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new G2pWord("world", List.of("W", "ER", "L", "D"))))
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("world", 1));
        assertThat(result.guidanceKr()).isNotBlank();
    }

    @Test
    @DisplayName("summarizeRecording: 여러 error 가 같은 단어를 가리키면 중복 제거")
    void duplicateWordHitsAreDeduplicated() {
        // canonicalIndex 1 and 2 both fall into word 0 "Hello" (phonemes 0..3).
        AnalyzeError e1 = new AnalyzeError("sub", 1, "EH", "AH");
        AnalyzeError e2 = new AnalyzeError("del", 2, null, "L");
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of(e1, e2))
                .g2pWords(List.of(
                        new G2pWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new G2pWord("world", List.of("W", "ER", "L", "D"))))
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("Hello", 0));
    }

    @Test
    @DisplayName("summarizeRecording: g2pWords 없으면 비례 매핑 추측 없이 빈 리스트 반환")
    void withoutG2pWordsReturnsEmpty() {
        AnalyzeError err = new AnalyzeError("sub", 5, "EH", "ER");
        LlmContext context = LlmContext.builder()
                .targetText("Hello world")
                .canonical(List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"))
                .errors(List.of(err))
                .build();

        RecordingGuidance result = generator.summarizeRecording(context);

        assertThat(result.wrongWords()).isEmpty();
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
