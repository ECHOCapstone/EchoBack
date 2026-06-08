package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// canonical(정답) ↔ perceived(인식) 음소열을 최소 편집거리로 정렬하는 결정적 정렬기.
// 점수 산출(ScoringService)의 단일 입력으로 쓰인다 — 화면 표시용 정렬은 LLM 이 따로 만들지만,
// 점수만큼은 LLM 판단에 의존하지 않고 두 음소열로부터 재현 가능하게 계산한다.
//
// op 는 canonical 순서로 나열된다:
//   MATCH        canonical[i] == perceived[j]
//   SUBSTITUTION canonical[i] != perceived[j] (둘 다 존재)
//   DELETION     canonical 에만 있음 (perceived 누락)
//   INSERTION    perceived 에만 있음 (canonical 에 없음, canonicalIndex = -1)
public final class PhonemeAligner {

    private PhonemeAligner() {}

    public static List<AlignmentOp> align(List<String> canonical, List<String> perceived) {
        List<String> ref = canonical == null ? List.of() : canonical;
        List<String> hyp = perceived == null ? List.of() : perceived;
        int n = ref.size();
        int m = hyp.size();

        // Needleman-Wunsch (Levenshtein) DP. d[i][j] = ref[0..i) 와 hyp[0..j) 의 최소 편집거리.
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int sub = equalPhoneme(ref.get(i - 1), hyp.get(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + sub);
            }
        }

        // 역추적: 대각선(MATCH/SUB) → 삭제 → 삽입 순으로 우선해 canonical 정렬을 안정적으로 만든다.
        List<AlignmentOp> ops = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                int sub = equalPhoneme(ref.get(i - 1), hyp.get(j - 1)) ? 0 : 1;
                if (d[i][j] == d[i - 1][j - 1] + sub) {
                    AlignmentOp.ErrorType type =
                            sub == 0 ? AlignmentOp.ErrorType.MATCH : AlignmentOp.ErrorType.SUBSTITUTION;
                    ops.add(new AlignmentOp(type, ref.get(i - 1), hyp.get(j - 1), i - 1));
                    i--;
                    j--;
                    continue;
                }
            }
            if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                ops.add(new AlignmentOp(AlignmentOp.ErrorType.DELETION, ref.get(i - 1), null, i - 1));
                i--;
                continue;
            }
            ops.add(new AlignmentOp(AlignmentOp.ErrorType.INSERTION, null, hyp.get(j - 1), -1));
            j--;
        }
        Collections.reverse(ops);
        return ops;
    }

    // ARPABET 대소문자 차이는 동일 음소로 본다 (모델/g2p 출력 케이스가 어긋나도 안전).
    private static boolean equalPhoneme(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
