package com.capstoneecho.echo_back.support;

import com.capstoneecho.echo_back.external.llm.RecordingGuidance;
import com.capstoneecho.echo_back.pronunciation.recording.dto.WrongWord;
import java.util.List;

/**
 * 컨트롤러 테스트에서 LlmClient 를 mock 할 때 재사용하는 응답 모음.
 */
public final class LlmMockResponses {

    public static RecordingGuidance defaultGuidance() {
        return new RecordingGuidance("발음을 더 또렷하게 따라 읽어 보세요.", List.of());
    }

    public static RecordingGuidance withWrongWord(String word, int index) {
        return new RecordingGuidance(
                "ɔ 모음을 더 둥글게 발음해 보세요.", List.of(new WrongWord(word, index)));
    }

    private LlmMockResponses() {
    }
}
