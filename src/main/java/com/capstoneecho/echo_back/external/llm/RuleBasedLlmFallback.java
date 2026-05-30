package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pWord;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

// 어떤 LLM provider 가 활성화되어 있든 항상 사용 가능한 규칙 기반 폴백.
// LlmClient 구현체는 외부 호출이 실패했을 때 이 컴포넌트의 결과로 떨어지면 된다.
// 모든 외부화 문구 / 단어 / 점수 임계값은 AppProperties 에서 주입한다.
@Component
public class RuleBasedLlmFallback {

    private final String recordingGuidance;
    private final String feedbackGuidance;
    private final String retryGuidance;
    private final String defaultPracticeWord;
    private final double passThreshold;

    public RuleBasedLlmFallback(AppProperties appProperties) {
        AppProperties.Messages m = appProperties.messages();
        this.recordingGuidance = safeMessage(m.recordingGuidanceFallback());
        this.feedbackGuidance = safeMessage(m.feedbackGuidanceFallback());
        this.retryGuidance = safeMessage(m.retryGuidanceFallback());
        AppProperties.Gamification g = appProperties.gamification();
        this.defaultPracticeWord = g.defaultPracticeWord();
        this.passThreshold = g.passThreshold();
    }

    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        int score = roundScore(context.currentScore());
        boolean retry = score < passThreshold;
        List<WrongWord> wrongs = resolveWrongWords(
                context.targetText(), context.errors(), context.g2pWords());
        return new LlmStepFeedback(
                score, retry, recordingGuidance, PronunciationGuide.empty(),
                List.of(), List.of(), wrongs, List.of());
    }

    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        int score = roundScore(context.currentScore());
        boolean correct = score >= passThreshold && context.errors().isEmpty();
        boolean retry = !correct;
        return new LlmRetryFeedback(score, correct, retry, retryGuidance, PronunciationGuide.empty(), List.of());
    }

    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        int score = roundScore(context.overallAccuracy());
        List<PracticeItem> next = List.of(
                new PracticeItem(defaultPracticeWord, PracticeItem.Kind.WORD, ""));
        return new LlmComprehensiveFeedback(score, feedbackGuidance, List.of(), List.of(), next);
    }

    // canonical phoneme 인덱스를 단어 경계로 환원해 약점 단어 목록을 만든다.
    // 단어 경계 정보 (g2pWords) 가 없으면 추측 매핑을 하지 않고 빈 리스트를 돌려준다.
    private static List<WrongWord> resolveWrongWords(
            String targetText, List<AnalyzeError> errors, List<G2pWord> g2pWords) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        String[] words = splitWords(targetText);
        if (words.length == 0) {
            return List.of();
        }
        int[] boundaries = cumulativePhonemeBoundaries(g2pWords);
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

    private static int roundScore(Double v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return (int) Math.round(v);
    }

    // LlmStepFeedback / LlmRetryFeedback / RecordingGuidance 의 non-empty 제약을 만족시키기 위해
    // 빈 문자열만은 피하도록 최소 공백 한 칸이라도 보장한다.
    private static String safeMessage(String s) {
        if (s == null || s.isBlank()) {
            return " ";
        }
        return s;
    }
}
