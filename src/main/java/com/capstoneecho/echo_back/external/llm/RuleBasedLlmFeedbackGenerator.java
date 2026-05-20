package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.external.llm.WrongWord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// LLM 호출 없이 규칙 기반으로 가이던스를 생성하는 기본 LlmClient 구현.
// 모든 폴백 문구와 단어는 app.messages / app.gamification 에서 주입한다.
@Component
@ConditionalOnProperty(name = "app.llm.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedLlmFeedbackGenerator implements LlmClient {

    private final String recordingGuidance;
    private final String feedbackGuidance;
    private final String retryGuidance;
    private final String defaultPracticeWord;

    public RuleBasedLlmFeedbackGenerator(AppProperties appProperties) {
        AppProperties.Messages m = appProperties.messages();
        this.recordingGuidance = m == null ? "" : m.recordingGuidanceFallback();
        this.feedbackGuidance = m == null ? "" : m.feedbackGuidanceFallback();
        this.retryGuidance = m == null ? "" : m.retryGuidanceFallback();
        AppProperties.Gamification g = appProperties.gamification();
        this.defaultPracticeWord = g == null ? "the" : g.defaultPracticeWord();
    }

    @Override
    public RecordingGuidance summarizeRecording(LlmContext context) {
        return new RecordingGuidance(recordingGuidance, resolveWrongWords(context));
    }

    @Override
    public String summarizeFeedback(LlmContext context) {
        return feedbackGuidance;
    }

    @Override
    public String retryGuidance(LlmContext context) {
        return retryGuidance;
    }

    // targetText 의 첫 단어를 추천한다. 비어 있으면 외부화된 폴백 단어로 떨어진다.
    @Override
    public String suggestPracticeWord(LlmContext context) {
        String[] words = splitWords(context.targetText());
        if (words.length > 0) {
            String first = stripPunctuation(words[0]);
            if (!first.isBlank()) {
                return first;
            }
        }
        return defaultPracticeWord;
    }

    // canonical phoneme 인덱스를 단어 경계로 환원해 wrong-word 목록을 만든다.
    // 단어 경계 정보 (g2pWords) 가 없으면 추측 매핑을 하지 않고 빈 리스트를 돌려준다.
    private List<WrongWord> resolveWrongWords(LlmContext context) {
        List<AnalyzeError> errors = context.errors();
        if (errors.isEmpty()) {
            return List.of();
        }
        String[] words = splitWords(context.targetText());
        if (words.length == 0) {
            return List.of();
        }
        int[] boundaries = cumulativePhonemeBoundaries(context.g2pWords());
        if (boundaries.length == 0) {
            return List.of();
        }
        int totalPhonemes = boundaries[boundaries.length - 1];
        int boundedWords = Math.min(words.length, boundaries.length);

        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        List<WrongWord> hits = new ArrayList<>();
        for (AnalyzeError error : errors) {
            Integer idx = error.canonicalIndex();
            if (idx == null || idx < 0 || idx >= totalPhonemes) {
                continue;
            }
            int wordIdx = wordIndexForPhoneme(boundaries, idx);
            if (wordIdx < 0 || wordIdx >= boundedWords) {
                continue;
            }
            String word = stripPunctuation(words[wordIdx]);
            if (word.isBlank()) {
                continue;
            }
            if (seen.add(wordIdx)) {
                hits.add(new WrongWord(word, wordIdx));
            }
        }
        return List.copyOf(hits);
    }

    // 단어별 phoneme 개수의 누적합. 인덱스 i 는 단어 i 까지의 phoneme 총합을 가리킨다.
    private static int[] cumulativePhonemeBoundaries(List<G2pWord> g2pWords) {
        if (g2pWords == null || g2pWords.isEmpty()) {
            return new int[0];
        }
        int[] cumulative = new int[g2pWords.size()];
        int running = 0;
        for (int i = 0; i < g2pWords.size(); i++) {
            List<String> phonemes = g2pWords.get(i).phonemes();
            running += phonemes == null ? 0 : phonemes.size();
            cumulative[i] = running;
        }
        return cumulative;
    }

    private static int wordIndexForPhoneme(int[] cumulative, int phonemeIdx) {
        for (int i = 0; i < cumulative.length; i++) {
            if (phonemeIdx < cumulative[i]) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitWords(String targetText) {
        if (targetText == null) {
            return new String[0];
        }
        String trimmed = targetText.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\s+");
    }

    private static String stripPunctuation(String word) {
        return word.replaceAll("^\\p{Punct}+|\\p{Punct}+$", "");
    }
}
