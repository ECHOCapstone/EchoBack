package com.capstoneecho.echo_back.learning.session.dto;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SessionDetailResponse(
        Long id,
        String title,
        String scriptText,
        boolean favorite,
        List<SessionSentenceResponse> sentences,
        Instant createdAt,
        Instant updatedAt
) {

    // 한국어 번역 맵을 받지 않는 기본 빌더. 목록 조회처럼 자막이 필요 없는 흐름에서 사용한다.
    public static SessionDetailResponse from(Session session) {
        return from(session, Map.of());
    }

    // 한국어 번역 맵 (영문 text → 한국어) 을 받아 SessionSentenceResponse 마다 자막을 함께 실어 응답한다.
    // 맵에 없으면 자막 없이 응답한다.
    public static SessionDetailResponse from(Session session, Map<String, String> translationsByText) {
        List<SessionSentenceResponse> sentenceDtos = session.getSentences().stream()
                .map(sentence -> SessionSentenceResponse.from(
                        sentence, translationsByText.get(sentence.getText())))
                .toList();
        return new SessionDetailResponse(
                session.getId(),
                session.getTitle(),
                session.getScriptText(),
                session.isFavorite(),
                sentenceDtos,
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    public record SessionSentenceResponse(
            Long id,
            int sentenceIndex,
            String text,
            String koreanTranslation
    ) {

        public static SessionSentenceResponse from(SessionSentence sentence, String koreanTranslation) {
            String safe = (koreanTranslation == null || koreanTranslation.isBlank()) ? null : koreanTranslation;
            return new SessionSentenceResponse(
                    sentence.getId(),
                    sentence.getSentenceIndex(),
                    sentence.getText(),
                    safe);
        }
    }
}
