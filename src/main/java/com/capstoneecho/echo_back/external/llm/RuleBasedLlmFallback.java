package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// 어떤 LLM provider 가 활성화돼 있어도 호출이 실패했을 때 안전 디폴트를 돌려준다.
// canonical 은 서비스 레이어가 결정해 context 에 넣어주므로 본 폴백은 표준 Levenshtein DP 로
// alignment 만 만들어 비지 않은 화면을 유지한다. 정교한 가중치 / 약점 음소 보정은 LLM 응답에서만 기대한다.
@Component
public class RuleBasedLlmFallback {

    private final RuntimeSettings settings;

    public RuleBasedLlmFallback(RuntimeSettings settings) {
        this.settings = settings;
    }

    public LlmStepFeedback stepFeedback(LlmStepContext context) {
        List<String> canonical = flatten(context.canonicalWords());
        AlignmentResult result = align(canonical, context.perceived());
        boolean retry = !result.errors.isEmpty();
        return new LlmStepFeedback(
                result.ops, result.errors, retry,
                safeMessage(settings.recordingGuidanceFallback()),
                PronunciationGuide.empty(),
                List.of(), List.of(), List.of(), List.of());
    }

    public LlmRetryFeedback retryFeedback(LlmRetryContext context) {
        List<String> canonical = flatten(context.canonicalWords());
        AlignmentResult result = align(canonical, context.perceived());
        boolean correct = result.errors.isEmpty();
        return new LlmRetryFeedback(
                result.ops, result.errors, correct, !correct,
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

    private static List<String> flatten(List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<String> flat = new ArrayList<>();
        for (CanonicalWord w : words) {
            if (w.phonemes() != null) {
                flat.addAll(w.phonemes());
            }
        }
        return flat;
    }

    private static int baselineScore(int matches, int canonicalSize) {
        if (canonicalSize <= 0) {
            return 0;
        }
        return roundScore(100.0 * matches / canonicalSize);
    }

    private static int roundScore(double v) {
        if (v < 0) return 0;
        if (v > 100) return 100;
        return (int) Math.round(v);
    }

    private static String safeMessage(String s) {
        return (s == null || s.isBlank()) ? " " : s;
    }

    // ----- 표준 Levenshtein DP 정렬 -----
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
                ops.add(0, new AlignmentOp(AlignmentOp.ErrorType.INSERTION, null, p.get(j - 1), -1));
                errors.add(0, new LlmPhonemeError(
                        AlignmentOp.ErrorType.INSERTION, null, p.get(j - 1), -1));
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
