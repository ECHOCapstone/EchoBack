package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.ModelAnalyzeResponse;
import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

// 모델 서버가 돌려준 정렬 항목을 도메인이 다루기 좋은 형태로 옮기고, 같은 항목들을 Recording 의
// errorsJson 컬럼에 직렬화·역직렬화하는 변환을 한 곳에서 처리한다.
//
// 직렬화 실패는 흔히 모델 서버 응답 포맷 변경이나 데이터 손상에서 비롯되는데, 호출자가
// step/feedback 흐름을 멈추는 것보다 빈 결과로 떨어지는 게 낫다. 다만 흔적이 남도록
// warn 로그를 남긴다.
@Component
public class PhonemeErrorMapper {

    private static final Logger log = LoggerFactory.getLogger(PhonemeErrorMapper.class);
    private static final TypeReference<List<ModelAnalyzeResponse.AlignmentItem>> ALIGNMENT_LIST =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public PhonemeErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<PhonemeErrorResponse> toResponses(List<ModelAnalyzeResponse.AlignmentItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        return items.stream()
                .map(e -> new PhonemeErrorResponse(e.op(), e.canonical(), e.recognized(), e.canonicalIndex()))
                .toList();
    }

    public String serialize(List<ModelAnalyzeResponse.AlignmentItem> items) {
        if (items == null || items.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("PhonemeError 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }

    public List<PhonemeErrorResponse> deserialize(String errorsJson) {
        if (errorsJson == null || errorsJson.isBlank()) return List.of();
        try {
            var items = objectMapper.<List<ModelAnalyzeResponse.AlignmentItem>>readValue(errorsJson, ALIGNMENT_LIST);
            return toResponses(items);
        } catch (Exception e) {
            log.warn("PhonemeError 역직렬화 실패 (skip): {}", e.getMessage());
            return List.of();
        }
    }
}
