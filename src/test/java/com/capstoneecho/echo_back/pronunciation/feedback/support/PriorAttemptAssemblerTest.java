package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PriorAttemptAssemblerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PriorAttemptAssembler assembler = new PriorAttemptAssembler(objectMapper);

    @Test
    @DisplayName("parseErrors: NULL / 빈 문자열 / 깨진 JSON → 빈 리스트")
    void parseErrorsHandlesEmptyAndBroken() {
        assertThat(assembler.parseErrors(null)).isEmpty();
        assertThat(assembler.parseErrors("")).isEmpty();
        assertThat(assembler.parseErrors("not-json")).isEmpty();
    }

    @Test
    @DisplayName("parseErrors: 정상 JSON 은 LlmPhonemeError 리스트로 역직렬화")
    void parseErrorsHappy() {
        String json = "[{\"op\":\"SUBSTITUTION\",\"canonicalIndex\":2,\"perceived\":\"AH\",\"canonical\":\"EH\"}]";
        List<LlmPhonemeError> errs = assembler.parseErrors(json);
        assertThat(errs).hasSize(1);
        assertThat(errs.get(0).op()).isEqualTo(AlignmentOp.ErrorType.SUBSTITUTION);
        assertThat(errs.get(0).canonicalIndex()).isEqualTo(2);
        assertThat(errs.get(0).canonical()).isEqualTo("EH");
    }

    @Test
    @DisplayName("fromRecordings(null / empty) → 빈 PriorAttempt 리스트")
    void fromEmpty() {
        assertThat(assembler.fromRecordings(null)).isEmpty();
        assertThat(assembler.fromRecordings(List.of())).isEmpty();
    }
}
