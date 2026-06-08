package com.capstoneecho.echo_back.external.modelserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// PhonemeRecognizer 의 두 경로: canonical 요구 시 순차 주입, 미요구 시 transcribe 병렬 실행.
class PhonemeRecognizerTest {

    private static final byte[] AUDIO = {1, 2, 3};

    private final ModelServerClient modelServerClient = Mockito.mock(ModelServerClient.class);
    private final RecognitionModelPolicy modelPolicy = Mockito.mock(RecognitionModelPolicy.class);
    // 테스트는 호출 스레드에서 동기 실행해 결정적으로 만든다.
    private final Executor directExecutor = Runnable::run;
    private final PhonemeRecognizer recognizer =
            new PhonemeRecognizer(modelServerClient, modelPolicy, directExecutor);

    private static TranscribeResult transcribeResult() {
        return new TranscribeResult(
                List.of("HH", "AH", "L", "OW"), List.of(0.9, 0.9, 0.9, 0.9),
                1.0, SpeechRate.NORMAL, 1.0, "model", "echo");
    }

    private static List<CanonicalWord> helloCanonical() {
        return List.of(new CanonicalWord("hello", List.of("HH", "AH", "L", "OW")));
    }

    @Test
    @DisplayName("canonical 을 요구하면 canonical 을 먼저 확보해 공백으로 이어 transcribe 에 주입한다")
    void requiresCanonicalInjectsCanonical() {
        when(modelPolicy.requiresCanonical()).thenReturn(true);
        when(modelServerClient.transcribe(eq(AUDIO), eq("HH AH L OW")))
                .thenReturn(transcribeResult());

        PhonemeRecognizer.Recognized result =
                recognizer.recognize(AUDIO, PhonemeRecognizerTest::helloCanonical);

        verify(modelServerClient).transcribe(eq(AUDIO), eq("HH AH L OW"));
        assertThat(result.canonicalPhonemes()).containsExactly("HH", "AH", "L", "OW");
        assertThat(result.canonicalWords()).hasSize(1);
    }

    @Test
    @DisplayName("canonical 을 요구하지 않으면 transcribe 에 canonical 을 넣지 않고 병렬 실행한다")
    void doesNotRequireCanonicalRunsParallel() {
        when(modelPolicy.requiresCanonical()).thenReturn(false);
        when(modelServerClient.transcribe(eq(AUDIO), isNull())).thenReturn(transcribeResult());
        AtomicBoolean canonicalResolved = new AtomicBoolean(false);

        PhonemeRecognizer.Recognized result = recognizer.recognize(AUDIO, () -> {
            canonicalResolved.set(true);
            return helloCanonical();
        });

        verify(modelServerClient).transcribe(eq(AUDIO), isNull());
        assertThat(canonicalResolved).isTrue();
        assertThat(result.canonicalPhonemes()).containsExactly("HH", "AH", "L", "OW");
    }

    @Test
    @DisplayName("병렬 경로에서 canonical 확보가 실패하면 그 예외를 전파한다")
    void parallelPathPropagatesCanonicalFailure() {
        when(modelPolicy.requiresCanonical()).thenReturn(false);
        when(modelServerClient.transcribe(eq(AUDIO), isNull())).thenReturn(transcribeResult());

        assertThatThrownBy(() -> recognizer.recognize(AUDIO, () -> {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED, "boom");
        }))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.CANONICAL_GENERATION_FAILED);
    }

    @Test
    @DisplayName("병렬 경로에서 transcribe 가 실패하면 원래의 BusinessException 을 풀어 전파한다")
    void parallelPathUnwrapsTranscribeFailure() {
        when(modelPolicy.requiresCanonical()).thenReturn(false);
        when(modelServerClient.transcribe(eq(AUDIO), isNull()))
                .thenThrow(new BusinessException(ErrorCode.MODEL_SERVER_ERROR, "5xx"));

        assertThatThrownBy(() -> recognizer.recognize(AUDIO, PhonemeRecognizerTest::helloCanonical))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.MODEL_SERVER_ERROR);
    }

    @Test
    @DisplayName("canonical 을 요구하지 않을 때 canonical 음소열을 transcribe 인자로 쓰지 않는다")
    void doesNotPassCanonicalStringWhenNotRequired() {
        when(modelPolicy.requiresCanonical()).thenReturn(false);
        when(modelServerClient.transcribe(eq(AUDIO), isNull())).thenReturn(transcribeResult());

        recognizer.recognize(AUDIO, PhonemeRecognizerTest::helloCanonical);

        verify(modelServerClient, never()).transcribe(eq(AUDIO), eq("HH AH L OW"));
    }
}
