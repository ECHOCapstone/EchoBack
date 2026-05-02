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

    Session getEntity(Long userId, Long sessionId);
}
