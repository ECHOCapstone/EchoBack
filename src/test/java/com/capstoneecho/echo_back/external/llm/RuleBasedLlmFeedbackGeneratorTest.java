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

// 규칙 기반 폴백 + LlmClient 어댑터가 구조화 record 를 그대로 채워 돌려주는지 검증한다.
// AppProperties 는 외부 yaml 없이 fixture 만으로 구성한다 (단위 테스트).
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
    @DisplayName("stepFeedback: 정확 일치 → score 100, errors 비고, retry 없음")
    void stepPerfectMatchYieldsFullScore() {
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(
                        new CanonicalWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new CanonicalWord("world", List.of("W", "ER", "L", "D"))),
                List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.score()).isEqualTo(100);
        assertThat(result.errors()).isEmpty();
        assertThat(result.alignment()).hasSize(8);
        assertThat(result.wrongWords()).isEmpty();
        assertThat(result.guidanceKr()).isNotBlank();
        assertThat(result.retryRecommended()).isFalse();
    }

    @Test
    @DisplayName("stepFeedback: 인식 오류가 canonicalWords 단어 경계로 환원돼 wrongWords 채운다")
    void errorsResolveToWordsViaCanonicalBoundaries() {
        LlmStepContext context = new LlmStepContext(
                "", "Hello world",
                List.of("HH", "AH", "L", "OW", "W", "EH", "L", "D"),
                List.of("HH", "AH", "L", "OW", "W", "ER", "L", "D"),
                List.of(
                        new CanonicalWord("Hello", List.of("HH", "AH", "L", "OW")),
                        new CanonicalWord("world", List.of("W", "ER", "L", "D"))),
                List.of());

        LlmStepFeedback result = generator.stepFeedback(context);

        assertThat(result.wrongWords()).containsExactly(new WrongWord("world", 1));
        assertThat(result.guidanceKr()).isNotBlank();
    }

    @Test
    @DisplayName("retryFeedback / comprehensiveFeedback 모두 항상 non-empty 가이던스를 제공한다")
    void retryAndComprehensiveAlwaysNonEmpty() {
        LlmRetryContext retry = new LlmRetryContext(
                "the",
                List.of("DH", "AH"),
                List.of("DH", "AH"),
                List.of(new CanonicalWord("the", List.of("DH", "AH"))),
                List.of());
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
