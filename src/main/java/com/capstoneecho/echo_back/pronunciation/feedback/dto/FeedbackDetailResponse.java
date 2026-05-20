package com.capstoneecho.echo_back.pronunciation.feedback.dto;

import com.capstoneecho.echo_back.external.llm.PracticeItem;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeError;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PhonemeOp;
import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

// 챕터 / 세션 종합 피드백 응답.
// 종합 LLM 결과 (strengths / weaknesses / nextPracticeItems) 는 엔티티 comprehensive_json 에서 역직렬화한다.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackDetailResponse(
        Long id,
        Long scriptId,
        Long sessionId,
        String title,
        double accuracy,
        String weakPhoneme,
        String practiceWord,
        String guidanceKr,
        List<String> strengths,
        List<String> weaknesses,
        List<PracticeItem> nextPracticeItems,
        List<PhonemeErrorView> errors,
        Instant createdAt,
        boolean completed,
        Instant completedAt
) {

    public record PhonemeErrorView(
            PhonemeOp op, String canonical, String perceived, Integer canonicalIndex) {
        public static PhonemeErrorView from(PhonemeError e) {
            return new PhonemeErrorView(
                    e.getOp(), e.getCanonical(), e.getPerceived(), e.getCanonicalIndex());
        }
    }

    public static FeedbackDetailResponse from(PronunciationFeedback fb, ObjectMapper objectMapper) {
        Long scriptId = fb.getScript() == null ? null : fb.getScript().getId();
        Long sessionId = fb.getSession() == null ? null : fb.getSession().getId();
        List<PhonemeErrorView> errors = fb.getErrors().stream()
                .map(PhonemeErrorView::from)
                .toList();
        Comprehensive c = parse(objectMapper, fb.getComprehensiveJson());
        return new FeedbackDetailResponse(
                fb.getId(),
                scriptId,
                sessionId,
                fb.getTitle(),
                fb.getAccuracy(),
                fb.getWeakPhoneme(),
                fb.getPracticeWord(),
                fb.getGuidanceKr(),
                c.strengths(),
                c.weaknesses(),
                c.nextPracticeItems(),
                errors,
                fb.getCreatedAt(),
                fb.isCompleted(),
                fb.getCompletedAt());
    }

    // ObjectMapper 가 안 들어오는 케이스 (테스트 등) 를 위한 폴백.
    public static FeedbackDetailResponse from(PronunciationFeedback fb) {
        return from(fb, null);
    }

    private static Comprehensive parse(ObjectMapper objectMapper, String json) {
        if (objectMapper == null || json == null || json.isBlank()) {
            return Comprehensive.empty();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Comprehensive>() {});
        } catch (Exception e) {
            return Comprehensive.empty();
        }
    }

    private record Comprehensive(
            List<String> strengths,
            List<String> weaknesses,
            List<PracticeItem> nextPracticeItems
    ) {
        static Comprehensive empty() {
            return new Comprehensive(List.of(), List.of(), List.of());
        }

        Comprehensive {
            strengths = strengths == null ? List.of() : strengths;
            weaknesses = weaknesses == null ? List.of() : weaknesses;
            nextPracticeItems = nextPracticeItems == null ? List.of() : nextPracticeItems;
        }
    }
}
