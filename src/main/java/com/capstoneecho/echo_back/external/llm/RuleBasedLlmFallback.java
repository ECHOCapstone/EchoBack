package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

// 어떤 LLM provider 가 활성화되어 있든 항상 사용 가능한 규칙 기반 폴백.
// LlmClient 구현체는 외부 호출이 실패했을 때 이 컴포넌트의 결과로 떨어지면 된다.
// 외부화 문구 / 단어 / 점수 임계값은 RuntimeSettings 에서 호출 시점에 읽는다 (어드민 편집 즉시 반영).
//
// 규칙 기반 정렬은 표준 Levenshtein DP — LLM 응답 실패 시에도 alignment / errors / score 가 비지 않도록
// 가벼운 fallback 으로 만든다. 가중 PER / 약점 음소 보정 같은 정교한 정책은 LLM 응답에서만 기대한다.
@Component
public class RuleBasedLlmFallback {

    private final RuntimeSettings settings;

    public RuleBasedLlmFallback(RuntimeSettings settings) {
        this.settings = settings;
    }

    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        AlignmentResult alignment = align(context.canonical(), context.perceived());
        int score = baselineScore(alignment, context.canonical().size());
        boolean retry = score < settings.passThreshold();
        List<WrongWord> wrongs = resolveWrongWords(
                context.targetText(), alignment.errors, context.canonicalWords());
        return new LlmStepFeedback(
                score, alignment.ops, alignment.errors, retry,
                safeMessage(settings.recordingGuidanceFallback()),
                PronunciationGuide.empty(),
                List.of(), List.of(), wrongs, List.of());
    }

    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        AlignmentResult alignment = align(context.canonical(), context.perceived());
        int score = baselineScore(alignment, context.canonical().size());
        boolean correct = score >= settings.passThreshold() && alignment.errors.isEmpty();
        boolean retry = !correct;
        return new LlmRetryFeedback(
                score, alignment.ops, alignment.errors, correct, retry,
                safeMessage(settings.retryGuidanceFallback()),
                PronunciationGuide.empty(), List.of());
    }

    public LlmComprehensiveFeedback comprehensiveFeedback(LlmComprehensiveContext context) {
        int score = roundScore(context.overallAccuracy());
        List<PracticeItem> next = List.of(
                new PracticeItem(settings.defaultPracticeWord(), PracticeItem.Kind.WORD, ""));
        return new LlmComprehensiveFeedback(
                score, safeMessage(settings.feedbackGuidanceFallback()), List.of(), List.of(), next);
    }

    // canonical phoneme 인덱스를 단어 경계로 환원해 약점 단어 목록을 만든다.
    // 단어 경계 정보 (canonicalWords) 가 없으면 추측 매핑을 하지 않고 빈 리스트를 돌려준다.
    private static List<WrongWord> resolveWrongWords(
            String targetText, List<LlmPhonemeError> errors, List<CanonicalWord> canonicalWords) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        String[] words = splitWords(targetText);
        if (words.length == 0) {
            return List.of();
        }
        int[] boundaries = cumulativePhonemeBoundaries(canonicalWords);
        if (boundaries.length == 0) {
            return List.of();
        }
        int totalPhonemes = boundaries[boundaries.length - 1];
        int boundedWords = Math.min(words.length, boundaries.length);

        LinkedHashSet<Integer> seen = new LinkedHashSet<>();
        List<WrongWord> hits = new ArrayList<>();
        for (LlmPhonemeError error : errors) {
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

    private static int[] cumulativePhonemeBoundaries(List<CanonicalWord> canonicalWords) {
        if (canonicalWords == null || canonicalWords.isEmpty()) {
            return new int[0];
        }
        int[] cumulative = new int[canonicalWords.size()];
        int running = 0;
        for (int i = 0; i < canonicalWords.size(); i++) {
            List<String> phonemes = canonicalWords.get(i).phonemes();
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

    private static int baselineScore(AlignmentResult result, int canonicalSize) {
        if (canonicalSize <= 0) {
            return 100;
        }
        int matches = result.matchCount;
        return roundScore(100.0 * matches / canonicalSize);
    }

    private static int roundScore(double v) {
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

    // ----- 표준 Levenshtein DP 정렬 -----
    // 한국 학습자 가중치 / 슈와 페널티 같은 정교한 정책은 LLM 응답에서만 기대하고, 본 폴백은 학습자가
    // 화면이 비지 않도록 최소 alignment + errors 를 만든다.
    static AlignmentResult align(List<String> canonical, List<String> perceived) {
        List<String> c = canonical == null ? List.of() : canonical;
        List<String> p = perceived == null ? List.of() : perceived;
        int n = c.size();
        int m = p.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (equalsPhoneme(c.get(i - 1), p.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    int sub = dp[i - 1][j - 1] + 1;
                    int del = dp[i - 1][j] + 1;
                    int ins = dp[i][j - 1] + 1;
                    dp[i][j] = Math.min(sub, Math.min(del, ins));
                }
            }
        }
        List<AlignmentOp> ops = new ArrayList<>();
        List<LlmPhonemeError> errors = new ArrayList<>();
        int i = n;
        int j = m;
        int matches = 0;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && equalsPhoneme(c.get(i - 1), p.get(j - 1))) {
                ops.add(0, new AlignmentOp(AlignmentOp.ErrorType.MATCH, c.get(i - 1), p.get(j - 1), i - 1));
                matches++;
                i--; j--;
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                ops.add(0, new AlignmentOp(AlignmentOp.ErrorType.SUBSTITUTION, c.get(i - 1), p.get(j - 1), i - 1));
                errors.add(0, new LlmPhonemeError(
                        AlignmentOp.ErrorType.SUBSTITUTION, c.get(i - 1), p.get(j - 1), i - 1));
                i--; j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                ops.add(0, new AlignmentOp(AlignmentOp.ErrorType.DELETION, c.get(i - 1), null, i - 1));
                errors.add(0, new LlmPhonemeError(
                        AlignmentOp.ErrorType.DELETION, c.get(i - 1), null, i - 1));
                i--;
            } else {
                ops.add(0, new AlignmentOp(AlignmentOp.ErrorType.INSERTION, null, p.get(j - 1), null));
                errors.add(0, new LlmPhonemeError(
                        AlignmentOp.ErrorType.INSERTION, null, p.get(j - 1), null));
                j--;
            }
        }
        return new AlignmentResult(ops, errors, matches);
    }

    private static boolean equalsPhoneme(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim()) && !a.trim().isEmpty();
    }

    record AlignmentResult(List<AlignmentOp> ops, List<LlmPhonemeError> errors, int matchCount) {}
}
