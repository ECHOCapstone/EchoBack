package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 규칙 기반 폴백 + LlmClient 어댑터가 구조화 record 를 그대로 채워 돌려주는지 검증한다.
// AppProperties 는 외부 yaml 없이 fixture 만으로 구성한다 (단위 테스트).
class RuleBasedLlmFeedbackGeneratorTest {

    private static final AppProperties FIXTURE = new AppProperties(
            null, null, null, null, null, null, null,
            new AppProperties.Gamification(
                    10, 7, 3, 5, 7, 70.0, 80.0, 10, "오늘의 랭킹", "the"),
            new AppProperties.Messages(
                    "발음을 더 또렷하게 따라 읽어 보세요.",
                    "꾸준한 연습이 발음 개선에 도움이 됩니다.",
                    "해당 단어를 한 번 더 천천히 따라 읽어 보세요.",
                    "업로드 가능한 파일 크기를 초과했습니다.",
                    "text 는 비어 있을 수 없습니다."),
            null, null);

    private final RuleBasedLlmFallback fallback = new RuleBasedLlmFallback(FIXTURE);
    private final RuleBasedLlmFeedbackGenerator generator = new RuleBasedLlmFeedbackGenerator(fallback);

    @Test
    @DisplayName("stepFeedback: errors 없음 → wrongWords 빈 배열 + non-empty guidance")
    void stepNoErrorsYieldsEmptyWrongWordsAndNonEmptyGuidance() {
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(), List.of(),
                null, 85.0, List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.wrongWords()).isEmpty();
        assertThat(result.guidanceKr()).isNotBlank();
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.retryRecommended()).isFalse();
    }

    @Test
    @DisplayName("stepFeedback: 음소 오류의 canonicalIndex 를 단어 경계로 환원해 wrongWords 채운다")
    void errorsResolveToWordsViaG2pBoundaries() {
        AnalyzeError err = new AnalyzeError("sub", 5, "EH", "ER");
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of("HH", "AH", "L", "OW", "W", "EH", "L", "D"),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(err),
                List.of(
                        new G2pWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new G2pWord("world", List.of("W", "ER", "L", "D"))),
                "ER", 70.0, List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("world", 1));
        assertThat(result.guidanceKr()).isNotBlank();
        assertThat(result.retryRecommended()).isTrue();
    }

    @Test
    @DisplayName("stepFeedback: 같은 단어를 가리키는 여러 error 는 중복 제거된다")
    void duplicateWordHitsAreDeduplicated() {
        AnalyzeError e1 = new AnalyzeError("sub", 1, "EH", "AH");
        AnalyzeError e2 = new AnalyzeError("del", 2, null, "L");
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of(),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(e1, e2),
                List.of(
                        new G2pWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new G2pWord("world", List.of("W", "ER", "L", "D"))),
                "AH", 60.0, List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("Hello", 0));
    }

    @Test
    @DisplayName("stepFeedback: g2pWords 없으면 추측 매핑 대신 빈 wrongWords 를 돌려준다")
    void withoutG2pWordsReturnsEmpty() {
        AnalyzeError err = new AnalyzeError("sub", 5, "EH", "ER");
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of(),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(err),
                List.of(),
                null, 75.0, List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.wrongWords()).isEmpty();
    }

    @Test
    @DisplayName("retryFeedback / comprehensiveFeedback 모두 항상 non-empty 가이던스를 제공한다")
    void retryAndComprehensiveAlwaysNonEmpty() {
        LlmRetryContext retry = new LlmRetryContext(
                "the", List.of(), List.of(), List.of(), null, 90.0, List.of());
        LlmComprehensiveContext comp = new LlmComprehensiveContext(
                "", "", List.of(), List.of(), null, 75.0);

        assertThat(generator.retryFeedback(retry).guidanceKr()).isNotBlank();
        assertThat(generator.comprehensiveFeedback(comp).summaryKr()).isNotBlank();
    }

    @Test
    @DisplayName("comprehensiveFeedback 은 항상 폴백 학습 단어를 한 개 이상 돌려준다")
    void comprehensiveAlwaysSuggestsAtLeastOneItem() {
        LlmComprehensiveContext context = new LlmComprehensiveContext(
                "기본 발음 트랙", "", List.of(), List.of(), null, 90.0);

        LlmComprehensiveFeedback result = generator.comprehensiveFeedback(context);

        assertThat(result.nextPracticeItems()).isNotEmpty();
        assertThat(result.nextPracticeItems().get(0).kind()).isEqualTo(PracticeItem.Kind.WORD);
    }
}
