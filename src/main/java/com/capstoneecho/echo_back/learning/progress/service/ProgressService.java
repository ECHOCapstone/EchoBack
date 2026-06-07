package com.capstoneecho.echo_back.learning.progress.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.progress.dto.ProgressResponse;
import com.capstoneecho.echo_back.learning.progress.entity.ChapterProgress;
import com.capstoneecho.echo_back.learning.progress.entity.SessionProgress;
import com.capstoneecho.echo_back.learning.progress.repository.ChapterProgressRepository;
import com.capstoneecho.echo_back.learning.progress.repository.SessionProgressRepository;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 챕터 / 세션 학습 진행 상태의 단일 진입점.
// 두 도메인의 정책이 동일 (조회 → upsert advance → 완료 시 reset) 하므로 같은 서비스에 모은다.
// FeedbackService 가 챕터 / 세션 완료 시점에 reset 을 호출하면 다음 진입에서 자동으로 첫 step 부터 시작한다.
@Service
@Transactional
public class ProgressService {

    private final ChapterProgressRepository chapterRepository;
    private final SessionProgressRepository sessionRepository;
    private final UserRepository userRepository;
    private final ScriptRepository scriptRepository;
    private final SessionRepository sessionEntityRepository;

    public ProgressService(
            ChapterProgressRepository chapterRepository,
            SessionProgressRepository sessionRepository,
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            SessionRepository sessionEntityRepository) {
        this.chapterRepository = chapterRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.sessionEntityRepository = sessionEntityRepository;
    }

    // 챕터 진행 상태 조회. 없으면 empty 응답 — 호출 측 (FE) 이 처음부터 시작한다.
    @Transactional(readOnly = true)
    public ProgressResponse getChapterProgress(Long userId, Long scriptId) {
        return chapterRepository.findByUser_IdAndScript_Id(userId, scriptId)
                .map(p -> ProgressResponse.of(p.getLastCompletedStepIndex()))
                .orElseGet(ProgressResponse::empty);
    }

    // step 완료를 반영한다. 진행 상태 행이 없으면 새로 만들고, 있으면 더 큰 인덱스로만 갱신한다.
    // 두 트랜잭션이 같은 사용자의 같은 챕터에서 동시에 INSERT 를 시도하면 unique 제약 위반이 한쪽에서 발생할 수
    // 있는데 (DataIntegrityViolationException), 그 시점에 다른 쪽이 이미 행을 만들었으므로 재조회 후 갱신하면 안전하다.
    public ProgressResponse advanceChapter(Long userId, Long scriptId, int stepIndex) {
        ChapterProgress progress = chapterRepository.findByUser_IdAndScript_Id(userId, scriptId)
                .orElseGet(() -> insertChapterProgressOrLoad(userId, scriptId));
        progress.advanceTo(stepIndex);
        return ProgressResponse.of(progress.getLastCompletedStepIndex());
    }

    private ChapterProgress insertChapterProgressOrLoad(Long userId, Long scriptId) {
        try {
            return chapterRepository.save(ChapterProgress.start(loadUser(userId), loadScript(scriptId)));
        } catch (DataIntegrityViolationException race) {
            return chapterRepository.findByUser_IdAndScript_Id(userId, scriptId)
                    .orElseThrow(() -> race);
        }
    }

    // "처음부터" 또는 챕터 완료 후 진행 상태 정리. 행이 없어도 안전 (idempotent).
    public void resetChapter(Long userId, Long scriptId) {
        chapterRepository.deleteByUser_IdAndScript_Id(userId, scriptId);
    }

    @Transactional(readOnly = true)
    public ProgressResponse getSessionProgress(Long userId, Long sessionId) {
        return sessionRepository.findByUser_IdAndSession_Id(userId, sessionId)
                .map(p -> ProgressResponse.of(p.getLastCompletedSentenceIndex()))
                .orElseGet(ProgressResponse::empty);
    }

    public ProgressResponse advanceSession(Long userId, Long sessionId, int sentenceIndex) {
        SessionProgress progress = sessionRepository.findByUser_IdAndSession_Id(userId, sessionId)
                .orElseGet(() -> insertSessionProgressOrLoad(userId, sessionId));
        progress.advanceTo(sentenceIndex);
        return ProgressResponse.of(progress.getLastCompletedSentenceIndex());
    }

    private SessionProgress insertSessionProgressOrLoad(Long userId, Long sessionId) {
        try {
            return sessionRepository.save(SessionProgress.start(loadUser(userId), loadSession(userId, sessionId)));
        } catch (DataIntegrityViolationException race) {
            return sessionRepository.findByUser_IdAndSession_Id(userId, sessionId)
                    .orElseThrow(() -> race);
        }
    }

    public void resetSession(Long userId, Long sessionId) {
        sessionRepository.deleteByUser_IdAndSession_Id(userId, sessionId);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Script loadScript(Long scriptId) {
        return scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
    }

    // 세션은 본인 소유만 진행 상태를 다룰 수 있다. 다른 사용자의 세션에는 SESSION_NOT_FOUND 로 응답한다.
    private Session loadSession(Long userId, Long sessionId) {
        return sessionEntityRepository.findByIdAndUser_Id(sessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }
}
