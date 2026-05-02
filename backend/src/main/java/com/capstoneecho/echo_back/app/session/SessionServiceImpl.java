package com.capstoneecho.echo_back.app.session;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.app.session.dto.SessionResponse;
import com.capstoneecho.echo_back.app.session.dto.SessionUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
class SessionServiceImpl implements SessionService {

    private final SessionRepository repository;

    SessionServiceImpl(SessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listMine(Long userId) {
        return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
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
        session.updateScript(request.scriptText());
        return SessionResponse.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public Session getEntity(Long userId, Long sessionId) {
        return repository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
