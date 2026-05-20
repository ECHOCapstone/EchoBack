package com.capstoneecho.echo_back.pronunciation.feedback.support;

import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.RetryAttempt;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// Recording / RetryAttempt 엔티티 목록을 LLM 호출용 PriorAttempt 리스트로 변환한다.
// errors_json 컬럼이 비어 있으면 빈 오류 리스트로 폴백해 호출이 깨지지 않게 한다.
@Component
public class PriorAttemptAssembler {

    private static final TypeReference<List<AnalyzeError>> ERROR_LIST_TYPE = new TypeReference<>() {};

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

    // 호환용 별칭. 기존 호출처 (Recording 만 받던 곳) 가 fromRecordings 와 같은 의미로 그대로 쓰도록 둔다.
    public List<PriorAttempt> from(List<Recording> recordings) {
        return fromRecordings(recordings);
    }

    public List<AnalyzeError> parseErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ERROR_LIST_TYPE);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
