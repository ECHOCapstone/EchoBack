package com.capstoneecho.echo_back.support;

import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import java.util.List;
import java.util.Optional;

/**
 * RecordingController/Service 테스트에서 재사용하는 ModelServerClient mock 응답 모음.
 */
public final class AnalyzeMockResponses {

    public static G2pResult helloG2p() {
        return new G2pResult("HH AH L OW", List.of());
    }

    public static AnalyzeResult perfect() {
        return new AnalyzeResult(
                List.of("HH", "AH", "L", "OW"),
                Optional.of(List.of("HH", "AH", "L", "OW")),
                List.of(0.9, 0.85, 0.8, 0.75),
                List.of(),
                List.of(),
                Optional.of(0.0),
                1.23);
    }

    public static AnalyzeResult withWaterError() {
        AnalyzeError err = new AnalyzeError("substitution", 1, "ʌ", "ɔ");
        return new AnalyzeResult(
                List.of("w", "ʌ", "t", "ɚ"),
                Optional.of(List.of("w", "ɔ", "t", "ɚ")),
                List.of(0.91, 0.62, 0.88, 0.79),
                List.of(),
                List.of(err),
                Optional.of(0.25),
                2.0);
    }

    private AnalyzeMockResponses() {
    }
}
