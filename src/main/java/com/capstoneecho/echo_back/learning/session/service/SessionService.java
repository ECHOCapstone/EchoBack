package com.capstoneecho.echo_back.learning.session.service;

import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;

import java.util.List;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.pronunciation.recording.service.RecordingService;
public interface SessionService {

    List<SessionDetailResponse> listMine(Long userId);

    SessionDetailResponse create(Long userId, SessionCreateRequest request);

    SessionDetailResponse get(Long userId, Long sessionId);

    SessionDetailResponse update(Long userId, Long sessionId, SessionPatchRequest request);

    void delete(Long userId, Long sessionId);

    // 사용자 맞춤 학습 도전 횟수 — Stats 에서 "맞춤 학습 도전자/마스터" 배지 평가 입력으로 사용된다.
    long countMine(Long userId);

    Session getEntity(Long userId, Long sessionId);

    // 한 문장 학습 흐름에서 사용자 소유 검증과 함께 SessionSentence 를 돌려준다.
    // RecordingService 가 sentenceId 로 녹음을 받을 때 targetText 를 결정하기 위한 진입점이다.
    SessionSentence getSentence(Long userId, Long sentenceId);
}
