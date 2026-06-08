package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.global.config.AppProperties;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

// alignment + errors → 0~100 정수 점수의 결정적 환산.
//   - 전 항목 MATCH 면 100.
//   - 그 외에는 베이스 = (MATCH 수 / canonical 길이) × 100 에서 페널티를 차감.
//     · 약점 음소 SUBSTITUTION / DELETION : -weakPenalty
//     · 그 외 SUBSTITUTION / DELETION    : -regularPenalty (보통 0)
//     · INSERTION                         : -insertionPenalty
//   - canonical 길이는 alignment 중 INSERTION 이 아닌 항목 수로 센다 (-1 인덱스 보정 없이).
// 약점 음소 집합과 페널티는 AppProperties.Scoring (yaml SSOT) 에서 로드한다.
@Service
public class ScoringService {

    private final Set<String> weakPhonemes;
    private final int weakPenalty;
    private final int insertionPenalty;
    private final int regularPenalty;

    public ScoringService(AppProperties appProperties) {
        AppProperties.Scoring scoring = appProperties.scoring();
        List<String> configured = scoring == null ? List.of() : scoring.safeWeakPhonemes();
        Set<String> upper = new HashSet<>(configured.size());
        for (String p : configured) {
            if (p == null) {
                continue;
            }
            String norm = p.trim().toUpperCase(Locale.ROOT);
            if (!norm.isEmpty()) {
                upper.add(norm);
            }
        }
        this.weakPhonemes = Set.copyOf(upper);
        this.weakPenalty = scoring == null ? 5 : scoring.weakPenalty();
        this.insertionPenalty = scoring == null ? 3 : scoring.insertionPenalty();
        this.regularPenalty = scoring == null ? 0 : scoring.regularPenalty();
    }

    public int compute(List<AlignmentOp> alignment, List<LlmPhonemeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return 100;
        }
        long canonicalLen = alignment == null ? 0L : alignment.stream()
                .filter(op -> op.errorType() != AlignmentOp.ErrorType.INSERTION)
                .count();
        if (canonicalLen == 0) {
            return 0;
        }
        long matches = alignment.stream()
                .filter(op -> op.errorType() == AlignmentOp.ErrorType.MATCH)
                .count();
        double base = (matches * 100.0) / canonicalLen;
        int penalty = 0;
        for (LlmPhonemeError err : errors) {
            AlignmentOp.ErrorType op = err.op();
            if (op == AlignmentOp.ErrorType.INSERTION) {
                penalty += insertionPenalty;
            } else if (isWeak(err.canonical())) {
                penalty += weakPenalty;
            } else {
                penalty += regularPenalty;
            }
        }
        int raw = (int) Math.round(base - penalty);
        return Math.max(0, Math.min(100, raw));
    }

    public boolean isWeak(String phoneme) {
        if (phoneme == null || phoneme.isBlank()) {
            return false;
        }
        return weakPhonemes.contains(phoneme.trim().toUpperCase(Locale.ROOT));
    }
}
