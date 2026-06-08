package com.capstoneecho.echo_back.external.llm.canonical;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// CanonicalWord 리스트 ↔ JSON 변환의 단일 출처. user_canonical_locks 와 daily_challenges 의
// canonical_cached_json 컬럼이 같은 포맷을 공유하므로 동일 직렬화 경로를 쓴다.
// 실패 시 BusinessException(CANONICAL_GENERATION_FAILED) — silent 폴백 금지.
@Component
public class CanonicalJson {

    private static final TypeReference<List<CanonicalWord>> WORDS_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public CanonicalJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(words);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                    "canonical 직렬화 실패");
        }
    }

    public List<CanonicalWord> deserialize(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<CanonicalWord> parsed = objectMapper.readValue(json, WORDS_TYPE);
            return parsed == null ? List.of() : parsed;
        } catch (JacksonException ex) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                    "canonical 본문 파싱 실패");
        }
    }
}
