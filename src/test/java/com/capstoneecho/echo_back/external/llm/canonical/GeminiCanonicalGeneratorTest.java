package com.capstoneecho.echo_back.external.llm.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.GeminiCallExecutor;
import com.capstoneecho.echo_back.external.llm.PromptCatalog;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

// GeminiCanonicalGenerator 의 CMU baseline + Gemini refine 2단계 흐름과 인벤토리 외 토큰 거부 동작을 검증한다.
class GeminiCanonicalGeneratorTest {

    @TempDir
    Path contentDir;

    private GeminiCallExecutor executor;
    private PromptCatalog prompts;
    private PhonemeInventory inventory;
    private ModelServerClient modelServerClient;
    private GeminiCanonicalGenerator generator;

    @BeforeEach
    void setUp() {
        executor = mock(GeminiCallExecutor.class);
        when(executor.isAvailable()).thenReturn(true);
        prompts = new PromptCatalog(
                contentDir.toString(), new PersistableContentStore(contentDir.toString()));
        inventory = new PhonemeInventory(new ObjectMapper());
        modelServerClient = mock(ModelServerClient.class);
        generator = new GeminiCanonicalGenerator(executor, prompts, inventory, modelServerClient);
    }

    @Test
    @DisplayName("perceived 없이 호출: /g2p baseline 을 받아 refine 한 결과를 그대로 돌려준다")
    void generateWithoutPerceivedCallsG2pThenRefine() {
        List<CanonicalWord> baseline = List.of(
                new CanonicalWord("the", List.of("DH", "AH")),
                new CanonicalWord("event", List.of("IH", "V", "EH", "N", "T")));
        when(modelServerClient.g2p("the event")).thenReturn(baseline);
        CanonicalResult refined = new CanonicalResult(List.of(
                new CanonicalWord("the", List.of("DH", "IY")),
                new CanonicalWord("event", List.of("IH", "V", "EH", "N", "T"))));
        when(executor.callRequired(anyString(), anyString(), any(), eq(CanonicalResult.class)))
                .thenReturn(refined);

        CanonicalResult got = generator.generate("the event", null);

        assertThat(got).isEqualTo(refined);
        verify(modelServerClient, times(1)).g2p("the event");
        verify(executor, times(1))
                .callRequired(anyString(), anyString(), any(Map.class), eq(CanonicalResult.class));
    }

    @Test
    @DisplayName("perceived 와 함께 호출: refine 프롬프트에 baseline 줄과 학습자 발음 섹션이 모두 들어간다")
    void generateWithPerceivedRendersBaselineAndPerceivedSection() {
        List<CanonicalWord> baseline = List.of(
                new CanonicalWord("water", List.of("W", "AO", "T", "ER")));
        when(modelServerClient.g2p("water")).thenReturn(baseline);
        CanonicalResult refined = new CanonicalResult(baseline);
        when(executor.callRequired(anyString(),
                contains("학습자가 실제로 발음한 음소: W AO D ER"),
                any(), eq(CanonicalResult.class)))
                .thenReturn(refined);

        CanonicalResult got = generator.generate("water", List.of("W", "AO", "D", "ER"));

        assertThat(got).isEqualTo(refined);
        verify(executor, times(1))
                .callRequired(anyString(),
                        contains("water — W AO T ER"),
                        any(Map.class),
                        eq(CanonicalResult.class));
    }

    @Test
    @DisplayName("인벤토리 외 음소가 refine 응답에 섞이면 1회 재시도 후에도 실패하면 BusinessException")
    void unknownPhonemesTriggerRetryThenFail() {
        when(modelServerClient.g2p("foo")).thenReturn(List.of(
                new CanonicalWord("foo", List.of("F", "UW"))));
        CanonicalResult bad = new CanonicalResult(List.of(
                new CanonicalWord("foo", List.of("FOO_NOT_REAL"))));
        when(executor.callRequired(anyString(), anyString(), any(), eq(CanonicalResult.class)))
                .thenReturn(bad);

        assertThatThrownBy(() -> generator.generate("foo", null))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CANONICAL_GENERATION_FAILED);

        verify(executor, times(2))
                .callRequired(anyString(), anyString(), any(Map.class), eq(CanonicalResult.class));
    }

    @Test
    @DisplayName("Gemini 미설정이면 /g2p 도 호출하지 않고 BusinessException 으로 끊는다")
    void unavailableExecutorFailsFastWithoutG2p() {
        when(executor.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> generator.generate("the", null))
                .isInstanceOf(BusinessException.class);

        verify(modelServerClient, times(0)).g2p(anyString());
    }

    @Test
    @DisplayName("모델 서버 /g2p 가 죽으면 그 예외가 그대로 전파된다 (silent fallback 금지)")
    void g2pFailurePropagates() {
        when(modelServerClient.g2p("the")).thenThrow(
                new BusinessException(ErrorCode.MODEL_SERVER_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> generator.generate("the", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE));
        verify(executor, times(0))
                .callRequired(anyString(), anyString(), any(Map.class), eq(CanonicalResult.class));
    }

    @Test
    @DisplayName("빈 입력은 빈 결과를 그대로 돌려준다 (/g2p, executor 모두 호출 없음)")
    void blankInputShortCircuits() {
        CanonicalResult got = generator.generate("  ", null);
        assertThat(got.words()).isEmpty();
        verify(modelServerClient, times(0)).g2p(anyString());
    }
}
