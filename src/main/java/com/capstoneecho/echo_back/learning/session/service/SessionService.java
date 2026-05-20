package com.capstoneecho.echo_back.learning.session.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.common.RequestValidator;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SentenceSplitter sentenceSplitter;
    private final RequestValidator requestValidator;

    public SessionService(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            SentenceSplitter sentenceSplitter,
            RequestValidator requestValidator) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sentenceSplitter = sentenceSplitter;
        this.requestValidator = requestValidator;
    }

    public List<SessionDetailResponse> list(Long userId) {
        return sessionRepository.findByUser_IdOrderByFavoriteDescUpdatedAtDesc(userId).stream()
                .map(SessionDetailResponse::from)
                .toList();
    }

    @Transactional
    public SessionDetailResponse create(Long userId, SessionCreateRequest request) {
        requestValidator.validate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Session session = Session.create(user, request.title());
        Session saved = sessionRepository.save(session);
        return SessionDetailResponse.from(saved);
    }

    public SessionDetailResponse get(Long userId, Long sessionId) {
        return SessionDetailResponse.from(loadOwnedSession(userId, sessionId));
    }

    // PATCH 는 부분 갱신이다: 본문이 null 이면 현재 상태를 그대로 돌려준다.
    @Transactional
    public SessionDetailResponse patch(Long userId, Long sessionId, SessionPatchRequest request) {
        if (request == null) {
            return SessionDetailResponse.from(loadOwnedSession(userId, sessionId));
        }
        requestValidator.validate(request);
        Session session = loadOwnedSession(userId, sessionId);
        if (request.title() != null && !request.title().isBlank()) {
            session.rename(request.title());
        }
        if (request.scriptText() != null) {
            session.updateScript(request.scriptText(), sentenceSplitter);
        }
        if (request.favorite() != null) {
            session.setFavorite(request.favorite());
        }
        return SessionDetailResponse.from(session);
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        int affected = sessionRepository.deleteByIdAndUser_Id(sessionId, userId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
    }

    private Session loadOwnedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
