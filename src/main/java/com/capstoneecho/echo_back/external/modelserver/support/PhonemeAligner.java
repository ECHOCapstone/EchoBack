package com.capstoneecho.echo_back.external.modelserver.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

// 정답 (canonical) 과 정규화된 인식 결과 (perceived) 두 음소 시퀀스를 표준 Levenshtein DP 로 정렬해
// substitution / deletion / insertion 시퀀스를 만들고, PER 는 WeightedPerCalculator 에 위임해
// 약점 음소 가중치 / insertion 가중치 같은 정책이 한 곳에서만 정의되도록 한다.
//
// 모델 서버의 alignment 가 비표준 음소 분해 전에 만들어진 경우 그 결과가 정답과 어긋나므로,
// 분해 후의 perceived 와 정답을 백엔드가 다시 정렬해 채점 / 시각화의 단일 출처가 되게 한다.
@Component
public class PhonemeAligner {

    private final WeightedPerCalculator weightedPerCalculator;

    public PhonemeAligner(WeightedPerCalculator weightedPerCalculator) {
        this.weightedPerCalculator = weightedPerCalculator;
    }

    public AlignmentResult align(List<String> canonical, List<String> perceived) {
        List<String> c = canonical == null ? List.of() : canonical;
        List<String> p = perceived == null ? List.of() : perceived;
        int n = c.size();
        int m = p.size();

        // dp[i][j] = canonical[0..i) 를 perceived[0..j) 로 만드는 최소 편집 비용.
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            dp[0][j] = j;
        }
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

        // 우상귀에서 출발해 op 시퀀스를 역추적한다. 동률일 때는 substitution → deletion → insertion 순으로
        // 골라 모델 서버 표기와 같은 우선순위를 유지한다.
        List<AnalyzeError> errors = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && equalsPhoneme(c.get(i - 1), p.get(j - 1))) {
                i--;
                j--;
                continue;
            }
            if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                errors.add(0, new AnalyzeError("substitution", i - 1, p.get(j - 1), c.get(i - 1)));
                i--;
                j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                errors.add(0, new AnalyzeError("deletion", i - 1, null, c.get(i - 1)));
                i--;
            } else {
                errors.add(0, new AnalyzeError("insertion", null, p.get(j - 1), null));
                j--;
            }
        }

        // 가중 PER 는 WeightedPerCalculator 가 단일 출처. canonical 이 비면 자체적으로 0.0 으로 처리한다.
        double per = weightedPerCalculator.computeWeightedPer(errors, n);
        return new AlignmentResult(errors, per);
    }

    // 음소 동일성 비교. 대소문자 / 좌우 공백을 흡수해 모델 출력의 사소한 표기 차이도 같은 음소로 인정한다.
    private static boolean equalsPhoneme(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim()) && !a.trim().isEmpty();
    }

    public record AlignmentResult(List<AnalyzeError> errors, double per) {}
}
