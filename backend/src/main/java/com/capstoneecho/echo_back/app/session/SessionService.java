package com.capstoneecho.echo_back.app.session;

import com.capstoneecho.echo_back.app.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.app.session.dto.SessionResponse;
import com.capstoneecho.echo_back.app.session.dto.SessionUpdateRequest;

import java.util.List;

public interface SessionService {

    List<SessionResponse> listMine(Long userId);

    SessionResponse create(Long userId, SessionCreateRequest request);

    SessionResponse get(Long userId, Long sessionId);

    SessionResponse update(Long userId, Long sessionId, SessionUpdateRequest request);

    void delete(Long userId, Long sessionId);

    Session getEntity(Long userId, Long sessionId);

    // 한 문장 학습 흐름에서 사용자 소유 검증과 함께 SessionSentence 를 돌려준다.
    // RecordingService 가 sentenceId 로 녹음을 받을 때 targetText 를 결정하기 위한 진입점이다.
    SessionSentence getSentence(Long userId, Long sentenceId);
}
