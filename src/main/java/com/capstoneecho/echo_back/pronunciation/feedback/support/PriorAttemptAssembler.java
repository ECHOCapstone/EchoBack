package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.RetryAttempt;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Recording / RetryAttempt 엔티티 목록을 LLM 호출용 PriorAttempt 리스트로 변환한다.
// errors_json 컬럼은 LlmPhonemeError 리스트 ([{op,canonical,perceived,canonicalIndex}, ...]) 로 저장된다.
@Component
public class PriorAttemptAssembler {

    private static final TypeReference<List<LlmPhonemeError>> ERROR_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public PriorAttemptAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PriorAttempt> fromRecordings(List<Recording> recordings) {
        if (recordings == null || recordings.isEmpty()) {
            return List.of();
        }
        List<PriorAttempt> out = new ArrayList<>(recordings.size());
        for (Recording r : recordings) {
            out.add(new PriorAttempt(
                    r.getCreatedAt(),
                    r.getStepScore(),
                    r.getPerceived(),
                    parseErrors(r.getErrorsJson())));
        }
        return out;
    }

    // RetryAttemptRepository 가 가장 최근 순으로 N개를 돌려주므로 LLM 입력은 오래된 순으로 뒤집어 넘긴다.
    public List<PriorAttempt> fromRetries(List<RetryAttempt> attempts) {
        if (attempts == null || attempts.isEmpty()) {
            return List.of();
        }
        List<PriorAttempt> out = new ArrayList<>(attempts.size());
        for (RetryAttempt a : attempts) {
            out.add(new PriorAttempt(
                    a.getCreatedAt(),
                    a.getScore(),
                    a.getPerceived(),
                    parseErrors(a.getErrorsJson())));
        }
        Collections.reverse(out);
        return out;
    }

    public List<LlmPhonemeError> parseErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ERROR_LIST_TYPE);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // LlmPhonemeError 목록을 errors_json 컬럼 문자열로 직렬화한다. 비어 있으면 NULL.
    public String toErrorsJson(List<LlmPhonemeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(errors);
    }
}
