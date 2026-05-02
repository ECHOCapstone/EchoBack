package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;

import java.util.List;

// 한국어 코칭 문장 생성의 단일 추상화. Generator 는 "무엇을 말할지" 에만 책임을 가진다.
// 재연습 단어 같은 도메인 데이터의 결정은 PracticeWordResolver 가 담당하며, 이 인터페이스가
// 그 책임을 침범하지 않는다 (SRP).
//
//   stepGuidance   - 한 녹음 직후 채팅 흐름에 노출되는 한 줄 가이드
//   unitGuidance   - 학습 unit 종료 시 종합 피드백 문장
//   retryGuidance  - 종합 피드백의 재연습 단어 평가 응답
public interface LlmFeedbackGenerator {

    String stepGuidance(
            String targetText,
            double stepScore,
            List<String> perceived,
            List<String> canonical,
            List<PhonemeErrorResponse> errors
    );

    String unitGuidance(
            String unitTitle,
            double accuracy,
            String weakPhoneme,
            List<PhonemeErrorResponse> errors
    );

    String retryGuidance(
            String practiceWord,
            boolean correct,
            List<String> perceived,
            List<String> canonical
    );
}
