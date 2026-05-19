package com.capstoneecho.echo_back.learning.session.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
@Service
@Transactional
class SessionServiceImpl implements SessionService {

    private final SessionRepository repository;
    private final SessionSentenceRepository sentenceRepository;
    private final SentenceSplitter sentenceSplitter;

    SessionServiceImpl(
            SessionRepository repository,
            SessionSentenceRepository sentenceRepository,
            SentenceSplitter sentenceSplitter
    ) {
        this.repository = repository;
        this.sentenceRepository = sentenceRepository;
        this.sentenceSplitter = sentenceSplitter;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listMine(Long userId) {
        return repository.findByUserIdOrderByFavoriteDescUpdatedAtDesc(userId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @Override
    public SessionResponse create(Long userId, SessionCreateRequest request) {
        var session = repository.save(Session.create(userId, request.title()));
        return SessionResponse.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public SessionResponse get(Long userId, Long sessionId) {
        return SessionResponse.from(getEntity(userId, sessionId));
    }

    @Override
    public SessionResponse update(Long userId, Long sessionId, SessionUpdateRequest request) {
        var session = getEntity(userId, sessionId);
        session.rename(request.title());
        // favorite 는 명시된 경우에만 반영. null 이면 기존 값 유지 (PATCH 부분 갱신 의미).
        if (request.favorite() != null) {
            session.setFavorite(request.favorite());
        }
        // scriptText 가 들어왔을 때만 분할 정책을 호출해 SessionSentence 컬렉션을 재구성한다.
        // 빈 문자열도 의도된 "대본 비우기" 로 보고 그대로 반영한다.
        if (request.scriptText() != null) {
            var sentences = sentenceSplitter.split(request.scriptText());
            session.updateScript(request.scriptText(), sentences);
            // 새로 추가된 SessionSentence 들의 자동 생성 id 가 응답 DTO 변환 전에 채워지도록
            // 명시 flush. saveAndFlush 가 dirty checking 결과를 즉시 DB 에 반영한다.
            repository.saveAndFlush(session);
        }
        return SessionResponse.from(session);
    }

    @Override
    public void delete(Long userId, Long sessionId) {
        var session = getEntity(userId, sessionId);
        repository.delete(session);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMine(Long userId) {
        return repository.countByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Session getEntity(Long userId, Long sessionId) {
        return repository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public SessionSentence getSentence(Long userId, Long sentenceId) {
        return sentenceRepository.findByIdAndSession_UserId(sentenceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_SENTENCE_NOT_FOUND));
    }
}
