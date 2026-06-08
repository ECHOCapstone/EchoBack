package com.capstoneecho.echo_back.support;

import com.capstoneecho.echo_back.external.modelserver.dto.SpeechRate;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import java.util.List;

// RecordingController / Service 테스트에서 재사용하는 ModelServerClient.transcribe mock 응답 모음.
public final class TranscribeMockResponses {

    public static TranscribeResult perfectTranscribe() {
        return new TranscribeResult(
                List.of("HH", "AH", "L", "OW"),
                List.of(0.9, 0.85, 0.8, 0.75),
                1.23,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }

    public static TranscribeResult misalignedTranscribe() {
        return new TranscribeResult(
                List.of("HH", "AH", "L"),
                List.of(0.91, 0.62, 0.88),
                2.0,
                SpeechRate.NORMAL,
                1.0,
                "echo-baseline",
                "echo");
    }

    private TranscribeMockResponses() {
    }
}
