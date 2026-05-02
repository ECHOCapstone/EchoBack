package com.capstoneecho.echo_back.app.feedback;

import com.capstoneecho.echo_back.app.feedback.dto.PhonemeErrorResponse;

import java.util.List;

// 채팅 흐름에 띄울 한국어 안내 문장을 만들어 준다.
//   stepGuidance  - 녹음 한 번 끝났을 때 보여주는 한 줄
//   unitGuidance  - 챕터 끝났을 때의 종합 코멘트
//   retryGuidance - 권장 단어를 재발음했을 때의 코멘트
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
