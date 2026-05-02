package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;

import java.util.List;

// 한국어 안내문 생성의 단일 추상화. 실 운영은 LLM 기반 구현체로 교체된다.
//   stepGuidance   - 한 녹음 직후 채팅 흐름에 노출되는 한 줄 가이드
//   generate       - 학습 unit 종료 시 종합 피드백
//   retryGuidance  - 종합 피드백의 재연습 단어 평가 응답
public interface LlmFeedbackGenerator {

    String stepGuidance(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    );

    Generated generate(String unitTitle, double accuracy, String weakPhoneme, List<PhonemeErrorResponse> errors);

    Generated retryGuidance(String practiceWord, boolean correct, List<String> perceived, List<String> canonical);

    record Generated(String guidanceKr, String practiceWord) {}
}
