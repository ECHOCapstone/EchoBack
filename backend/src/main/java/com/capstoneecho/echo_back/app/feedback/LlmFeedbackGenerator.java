package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;

import java.util.List;

// 채팅 흐름에 띄울 한국어 안내 문장을 만들어 준다.
//   stepGuidance           - 녹음 한 번 끝났을 때 보여주는 한 줄
//   unitGuidance           - 챕터 끝났을 때의 종합 코멘트
//   evaluateRetry          - 권장 단어 재발음의 정/오 판정 + 한국어 코멘트를 한 번에 돌려준다
//   recommendPracticeWord  - 약점 음소 연습에 어울리는 영어 단어 하나 추천
//                            (빈 문자열을 돌려주면 호출자는 자체 fallback 으로 떨어진다)
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

    RetryEvaluation evaluateRetry(
            String practiceWord,
            List<String> perceived,
            List<String> canonical
    );

    String recommendPracticeWord(String unitTitle, String weakPhoneme);

    record RetryEvaluation(boolean correct, String guidance) {}
}
