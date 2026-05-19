package com.capstoneecho.echo_back.app.session.dto;

import com.capstoneecho.echo_back.app.session.SessionSentence;

// 사용자 맞춤 세션의 학습 단위 한 문장. 프론트는 sentenceIndex 순으로 노출하고,
// id 를 그대로 녹음 업로드 (sentenceId) 키로 사용한다.
public record SessionSentenceResponse(
        Long id,
        int sentenceIndex,
        String text
) {

    public static SessionSentenceResponse from(SessionSentence sentence) {
        return new SessionSentenceResponse(
                sentence.getId(),
                sentence.getSentenceIndex(),
                sentence.getText()
        );
    }
}
