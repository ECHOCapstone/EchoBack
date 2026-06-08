package com.capstoneecho.echo_back.external.modelserver;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

// 음성 인식(/transcribe)과 canonical 확보의 실행 순서를 모델 특성에 맞춰 조율한다.
//
//   - canonical 을 요구하는 모델(FiLM): canonical 을 먼저 확보해 변조 조건으로 주입한 뒤 /transcribe (순차).
//   - 요구하지 않는 모델(baseline·slplab): /transcribe 를 전용 풀에서 즉시 시작하고, 호출 스레드는
//     canonical 확보(콘텐츠 캐시 조회 또는 LLM 생성)를 동시에 진행한 뒤 둘을 합친다.
//
// canonicalSupplier 는 호출 스레드에서 실행되므로 트랜잭션/영속성 컨텍스트가 유지된다. 워커 스레드로
// 분리되는 것은 transcribe 뿐이며, RestClient 와 in-memory SettingsService 만 사용해 스레드 안전하다.
@Component
public class PhonemeRecognizer {

    private final ModelServerClient modelServerClient;
    private final RecognitionModelPolicy modelPolicy;
    private final Executor transcribeExecutor;

    public PhonemeRecognizer(
            ModelServerClient modelServerClient,
            RecognitionModelPolicy modelPolicy,
            Executor recognitionTranscribeExecutor) {
        this.modelServerClient = modelServerClient;
        this.modelPolicy = modelPolicy;
        this.transcribeExecutor = recognitionTranscribeExecutor;
    }

    // canonicalWords 의 음소를 단어 순서대로 평탄화한다.
    public static List<String> flattenCanonical(List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<String> flat = new ArrayList<>();
        for (CanonicalWord word : words) {
            if (word.phonemes() != null) {
                flat.addAll(word.phonemes());
            }
        }
        return flat;
    }

    // 음성을 인식하고, 함께 쓰일 canonical 단어열/음소열을 돌려준다.
    // canonicalSupplier 는 호출 스레드에서 정확히 한 번 실행된다.
    public Recognized recognize(byte[] audioBytes, Supplier<List<CanonicalWord>> canonicalSupplier) {
        if (modelPolicy.requiresCanonical()) {
            List<CanonicalWord> words = canonicalSupplier.get();
            List<String> phonemes = flattenCanonical(words);
            TranscribeResult transcribe = modelServerClient.transcribe(
                    audioBytes, AnalysisSnapshotFormat.joinTokens(phonemes));
            return new Recognized(transcribe, words, phonemes);
        }

        // canonical 불필요 — transcribe 를 동시에 시작해 canonical 확보 시간과 겹친다.
        CompletableFuture<TranscribeResult> pending = CompletableFuture.supplyAsync(
                () -> modelServerClient.transcribe(audioBytes, null), transcribeExecutor);
        List<CanonicalWord> words;
        try {
            words = canonicalSupplier.get();
        } catch (RuntimeException e) {
            pending.cancel(true);
            throw e;
        }
        TranscribeResult transcribe = await(pending);
        return new Recognized(transcribe, words, flattenCanonical(words));
    }

    // CompletableFuture.join 이 감싸는 CompletionException 을 풀어 원래의 (Business)Exception 을 전파한다.
    private static TranscribeResult await(CompletableFuture<TranscribeResult> pending) {
        try {
            return pending.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    // 인식 결과 묶음: perceived 를 담은 transcribe, canonical 단어열, 평탄화한 canonical 음소열.
    public record Recognized(
            TranscribeResult transcribe,
            List<CanonicalWord> canonicalWords,
            List<String> canonicalPhonemes) {}
}
