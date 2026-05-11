package com.capstoneecho.echo_back.learning.session.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionSummaryResponse;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SentenceSplitter sentenceSplitter;
    private final Validator validator;

    public SessionService(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            SentenceSplitter sentenceSplitter,
            Validator validator) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sentenceSplitter = sentenceSplitter;
        this.validator = validator;
    }

    public List<SessionSummaryResponse> list(Long userId) {
        return sessionRepository.findAllByUser_IdOrderByUpdatedAtDesc(userId).stream()
                .map(SessionSummaryResponse::from)
                .toList();
    }

    @Transactional
    public SessionDetailResponse create(Long userId, SessionCreateRequest request) {
        validate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Session session = Session.create(user, request.title());
        Session saved = sessionRepository.save(session);
        return SessionDetailResponse.from(saved);
    }

    public SessionDetailResponse get(Long userId, Long sessionId) {
        return SessionDetailResponse.from(loadOwnedSession(userId, sessionId));
    }

    @Transactional
    public SessionDetailResponse patch(Long userId, Long sessionId, SessionPatchRequest request) {
        if (request == null) {
            return SessionDetailResponse.from(loadOwnedSession(userId, sessionId));
        }
        validate(request);
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

    private <T> void validate(T request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }
}
