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
import com.capstoneecho.echo_back.translation.dto.TranslationResponse;
import com.capstoneecho.echo_back.translation.service.TranslationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SentenceSplitter sentenceSplitter;
    private final RequestValidator requestValidator;
    private final TranslationService translationService;

    public SessionService(
            SessionRepository sessionRepository,
            UserRepository userRepository,
            SentenceSplitter sentenceSplitter,
            RequestValidator requestValidator,
            TranslationService translationService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sentenceSplitter = sentenceSplitter;
        this.requestValidator = requestValidator;
        this.translationService = translationService;
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

    // 학습 시작 시 호출되는 단일 조회. 한국어 자막을 함께 실어 응답해 FE 가 별도 호출 없이 즉시 노출한다.
    // 클래스 기본은 readOnly 이지만 TranslationService 의 캐시 저장 (쓰기) 을 호출하므로 메서드 단위로 해제한다.
    @Transactional
    public SessionDetailResponse get(Long userId, Long sessionId) {
        Session session = loadOwnedSession(userId, sessionId);
        return SessionDetailResponse.from(session, resolveTranslations(session));
    }

    // PATCH 는 부분 갱신이다: 본문이 null 이면 현재 상태를 그대로 돌려준다.
    // 응답에 한국어 자막도 함께 실어 보내, 스크립트를 새로 입력하자마자 학습에 들어가도 자막이 즉시 보이게 한다.
    @Transactional
    public SessionDetailResponse patch(Long userId, Long sessionId, SessionPatchRequest request) {
        if (request == null) {
            // 본문 없는 PATCH 는 사실상 단순 read — 번역 외부 호출 없이 현재 상태만 돌려준다.
            // 자막이 필요한 학습 진입은 GET /api/sessions/{id} 로 분리돼 있어 UX 손상이 없다.
            return SessionDetailResponse.from(loadOwnedSession(userId, sessionId));
        }
        requestValidator.validate(request);
        Session session = loadOwnedSession(userId, sessionId);
        if (request.title() != null && !request.title().isBlank()) {
            session.rename(request.title());
        }
        boolean scriptChanged = request.scriptText() != null;
        if (scriptChanged) {
            session.updateScript(request.scriptText(), sentenceSplitter);
        }
        if (request.favorite() != null) {
            session.setFavorite(request.favorite());
        }
        // scriptText 갱신은 새 SessionSentence 들을 cascade 로 INSERT 한다.
        // IDENTITY 키라 commit 전까지 id 가 채워지지 않으므로, 응답을 만들기 전에 명시적으로 flush 해
        // 새 sentence 의 id 를 확보한다. 안 하면 응답의 sentences[].id 가 null 로 나가 클라이언트의
        // sessionSentenceId 가 빠진 채 업로드돼 detectMode 가 INVALID_REQUEST 로 거절한다.
        if (scriptChanged) {
            sessionRepository.flush();
        }
        return SessionDetailResponse.from(session, resolveTranslations(session));
    }

    // 세션의 sentence 들의 영문 text 를 모아 한 번에 번역을 받아 매핑으로 돌려준다.
    // 외부 호출이 실패하거나 noop 폴백이면 빈 맵 → 응답에 자막 없이 진행한다.
    private Map<String, String> resolveTranslations(Session session) {
        List<String> texts = new ArrayList<>();
        for (var sentence : session.getSentences()) {
            String text = sentence.getText();
            if (text != null && !text.isBlank() && !texts.contains(text)) {
                texts.add(text);
            }
        }
        if (texts.isEmpty()) {
            return Map.of();
        }
        TranslationResponse translated = translationService.translate(texts);
        Map<String, String> out = new HashMap<>();
        for (TranslationResponse.Item item : translated.items()) {
            if (item.target() != null && !item.target().isBlank()) {
                out.put(item.source(), item.target());
            }
        }
        return out;
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
