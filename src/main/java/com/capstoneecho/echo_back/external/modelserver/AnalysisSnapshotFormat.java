package com.capstoneecho.echo_back.external.modelserver;

import java.util.List;

// 모델 서버 분석 결과의 토큰열을 Recording / RetryAttempt 의 스냅샷 컬럼 문자열로 직렬화한다.
// 비어 있으면 컬럼을 비우도록 null 을 돌린다.
public final class AnalysisSnapshotFormat {

    private AnalysisSnapshotFormat() {}

    // 음소 / 단어 토큰을 공백으로 잇는다.
    public static String joinTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return String.join(" ", tokens);
    }

    // 프레임별 softmax 확률을 공백으로 잇는다. 누락된 값은 0 으로 채운다.
    public static String joinSoftmax(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            Double v = values.get(i);
            sb.append(v == null ? "0" : v.toString());
        }
        return sb.toString();
    }
}
