package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.settings.AppSettingRepository;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.global.settings.SettingsService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// 규칙 기반 폴백 + LlmClient 어댑터의 안전 디폴트 동작 검증.
// canonical 은 서비스 레이어가 context 에 넣어주므로 본 폴백은 표준 Levenshtein DP 로 alignment 를 만든다.
class RuleBasedLlmFeedbackGeneratorTest {

    private static final AppProperties FIXTURE = new AppProperties(
            null, null, null, null, null, null, null,
            new AppProperties.Gamification(
                    10, 7, 3, 5, 7, 75.0, 10, "the"),
            null,
            new AppProperties.Messages(
                    "발음을 더 또렷하게 따라 읽어 보세요.",
                    "꾸준한 연습이 발음 개선에 도움이 됩니다.",
                    "해당 단어를 한 번 더 천천히 따라 읽어 보세요.",
                    "업로드 가능한 파일 크기를 초과했습니다.",
                    "text 는 비어 있을 수 없습니다."),
            null, null, null, null, null);

    // 오버라이드 없는 SettingsService → RuntimeSettings 가 FIXTURE 의 yaml 기본값을 그대로 돌려준다.
    private final RuntimeSettings runtimeSettings =
            new RuntimeSettings(new SettingsService(mock(AppSettingRepository.class)), FIXTURE);
    private final RuleBasedLlmFallback fallback = new RuleBasedLlmFallback(runtimeSettings);
    private final RuleBasedLlmFeedbackGenerator generator = new RuleBasedLlmFeedbackGenerator(fallback);

    @Test
    @DisplayName("stepFeedback: canonical 과 perceived 가 정확히 일치하면 score=100, errors 비고, retry 없음")
    void stepPerfectMatchYieldsFullScore() {
        LlmStepContext context = new LlmStepContext(
                "", "Hello",
                List.of(new CanonicalWord("hello", List.of("HH", "AH", "L", "OW"))),
                List.of("HH", "AH", "L", "OW"),
                List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.errors()).isEmpty();
        assertThat(result.alignment()).hasSize(4);
        assertThat(result.retryRecommended()).isFalse();
        assertThat(result.guidanceKr()).isNotBlank();
    }

    @Test
    @DisplayName("stepFeedback: canonical 비어있고 perceived 만 있으면 모두 INSERTION + score=0 + retry 권고")
    void stepEmptyCanonicalProducesInsertions() {
        LlmStepContext context = new LlmStepContext(
                "", "",
                List.of(),
                List.of("HH", "AH", "L", "OW"),
                List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.score()).isZero();
        assertThat(result.alignment()).hasSize(4);
        assertThat(result.alignment())
                .allMatch(op -> op.errorType() == AlignmentOp.ErrorType.INSERTION);
        assertThat(result.errors()).hasSize(4);
        assertThat(result.retryRecommended()).isTrue();
    }

    @Test
    @DisplayName("retryFeedback / comprehensiveFeedback 모두 항상 non-empty 가이던스를 제공한다")
    void retryAndComprehensiveAlwaysNonEmpty() {
        LlmRetryContext retry = new LlmRetryContext(
                "the",
                List.of(new CanonicalWord("the", List.of("DH", "AH"))),
                List.of("DH", "AH"),
                List.of());
        LlmComprehensiveContext comp = new LlmComprehensiveContext(
                "", "", List.of(), List.of(), null, 75.0);

        LlmRetryFeedback retryResult = generator.retryFeedback(retry);
        assertThat(retryResult.guidanceKr()).isNotBlank();
        assertThat(retryResult.score()).isEqualTo(100);
        assertThat(retryResult.correct()).isTrue();

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
