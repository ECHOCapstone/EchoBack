package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.llm.RecordingGuidance;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final UserRepository userRepository;
    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;
    private final SessionRepository sessionRepository;
    private final SessionSentenceRepository sentenceRepository;
    private final RecordingRepository recordingRepository;
    private final RecordingStorage recordingStorage;
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final WavHeaderValidator wavHeaderValidator;
    private final ScoringPolicy scoringPolicy;

    public RecordingService(
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository stepRepository,
            SessionRepository sessionRepository,
            SessionSentenceRepository sentenceRepository,
            RecordingRepository recordingRepository,
            RecordingStorage recordingStorage,
            ModelServerClient modelServerClient,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            WavHeaderValidator wavHeaderValidator,
            ScoringPolicy scoringPolicy) {
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.sentenceRepository = sentenceRepository;
        this.recordingRepository = recordingRepository;
        this.recordingStorage = recordingStorage;
        this.modelServerClient = modelServerClient;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.wavHeaderValidator = wavHeaderValidator;
        this.scoringPolicy = scoringPolicy;
    }

    // 녹음 1건을 업로드한다: 헤더 검증 → 부모 (script/step 또는 session/sentence) 매핑 →
    // G2P+분석 → LLM 요약 → 디스크 저장 → 엔티티 저장 순. 트랜잭션이 깨지면 파일도 같이 정리한다.
    @Transactional
    public RecordingUploadResponse upload(
            Long userId, RecordingUploadRequest request, byte[] audioBytes) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);

        Mode mode = detectMode(request);
        ResolvedParents parents = resolveParents(userId, request, mode);
        String targetText = resolveTargetText(parents);

        G2pResult g2p = modelServerClient.g2p(targetText);
        String canonical = g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);

        LlmContext context = LlmContext.builder()
                .targetText(targetText)
                .perceived(analyze.perceived())
                .canonical(analyze.canonicalOrEmpty())
                .errors(analyze.errors())
                .g2pWords(g2p.words())
                .build();
        RecordingGuidance guidance = llmClient.summarizeRecording(context);

        String audioPath = recordingStorage.save(userId, audioBytes);
        registerStorageCleanupOnNonCommit(audioPath);

        Recording recording = buildRecording(mode, parents, audioPath, targetText);
        recording.applyWrongWordsJson(serializeWrongWords(guidance.wrongWords()));
        Recording saved = recordingRepository.save(recording);

        return toResponse(saved, parents, analyze, guidance);
    }

    private String serializeWrongWords(List<WrongWord> wrongWords) {
        if (wrongWords == null || wrongWords.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(wrongWords);
        } catch (RuntimeException ex) {
            log.warn(
                    "Failed to serialize wrongWords ({} items) for recording; persisting NULL",
                    wrongWords.size(),
                    ex);
            return null;
        }
    }

    // 트랜잭션이 롤백되면 방금 저장한 오디오 파일이 orphan 으로 남는 것을 막는다.
    private void registerStorageCleanupOnNonCommit(String audioPath) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    recordingStorage.delete(audioPath);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Failed to clean up orphan recording audio after non-commit (path={})",
                            audioPath,
                            ex);
                }
            }
        });
    }

    // 요청에 어떤 부모 식별자가 채워졌는지로 두 흐름 (스크립트-스텝 / 세션-문장) 을 구분한다.
    private Mode detectMode(RecordingUploadRequest r) {
        boolean hasScript = r.scriptId() != null;
        boolean hasStep = r.stepId() != null;
        boolean hasSession = r.sessionId() != null;
        boolean hasSentence = r.sessionSentenceId() != null;
        if (hasScript && hasStep && !hasSession && !hasSentence) {
            return Mode.SCRIPT_FLOW;
        }
        if (!hasScript && !hasStep && hasSession && hasSentence) {
            return Mode.SESSION_SENTENCE;
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private ResolvedParents resolveParents(Long userId, RecordingUploadRequest r, Mode mode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return switch (mode) {
            case SCRIPT_FLOW -> {
                Script script = scriptRepository.findById(r.scriptId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
                LearningStep step = stepRepository.findById(r.stepId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.STEP_NOT_FOUND));
                if (step.getScript() == null
                        || !step.getScript().getId().equals(script.getId())) {
                    throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
                }
                yield new ResolvedParents(user, script, step, null, null);
            }
            case SESSION_SENTENCE -> {
                Session session = sessionRepository.findByIdAndUser_Id(r.sessionId(), userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
                SessionSentence sentence = sentenceRepository.findById(r.sessionSentenceId())
                        .orElseThrow(() ->
                                new BusinessException(ErrorCode.SESSION_SENTENCE_NOT_FOUND));
                if (sentence.getSession() == null
                        || !sentence.getSession().getId().equals(session.getId())) {
                    throw new BusinessException(ErrorCode.SESSION_SENTENCE_NOT_FOUND);
                }
                yield new ResolvedParents(user, null, null, session, sentence);
            }
        };
    }

    // 부모 컨텍스트에서 분석 대상 텍스트를 뽑는다. 단계 → 문장 → 전체 스크립트 순으로 우선한다.
    private String resolveTargetText(ResolvedParents parents) {
        if (parents.step() != null) {
            String t = parents.step().getTargetText();
            return t == null ? "" : t;
        }
        if (parents.sentence() != null) {
            String t = parents.sentence().getText();
            return t == null ? "" : t;
        }
        if (parents.session() != null) {
            String t = parents.session().getScriptText();
            return t == null ? "" : t;
        }
        return "";
    }

    private Recording buildRecording(
            Mode mode, ResolvedParents p, String audioPath, String targetText) {
        return switch (mode) {
            case SCRIPT_FLOW -> Recording.forScriptStep(
                    p.user(), p.script(), p.step(), audioPath, targetText);
            case SESSION_SENTENCE -> Recording.forSessionSentence(
                    p.user(), p.session(), p.sentence(), audioPath, targetText);
        };
    }

    private RecordingUploadResponse toResponse(
            Recording saved,
            ResolvedParents parents,
            AnalyzeResult analyze,
            RecordingGuidance guidance) {
        List<RecordingUploadResponse.PhonemeErrorView> errorViews = analyze.errors().stream()
                .map(RecordingService::toErrorView)
                .toList();
        Double stepScore = analyze.per() == null ? null : scoringPolicy.perToScore(analyze.per());
        Long scriptId = parents.script() == null ? null : parents.script().getId();
        Long stepId = parents.step() == null ? null : parents.step().getId();
        Long sessionId = parents.session() == null ? null : parents.session().getId();
        Long sentenceId = parents.sentence() == null ? null : parents.sentence().getId();

        return new RecordingUploadResponse(
                saved.getId(),
                scriptId,
                sessionId,
                stepId,
                sentenceId,
                analyze.durationSec(),
                List.copyOf(analyze.perceived()),
                List.copyOf(analyze.canonicalOrEmpty()),
                List.copyOf(analyze.peakSoftmax()),
                stepScore,
                guidance.guidanceKr(),
                errorViews,
                List.<WrongWord>copyOf(guidance.wrongWords()),
                saved.getCreatedAt());
    }

    private static RecordingUploadResponse.PhonemeErrorView toErrorView(AnalyzeError e) {
        return new RecordingUploadResponse.PhonemeErrorView(
                e.op(), e.canonical(), e.perceived(), e.canonicalIndex());
    }

    private enum Mode {
        SCRIPT_FLOW,
        SESSION_SENTENCE
    }

    private record ResolvedParents(
            User user,
            Script script,
            LearningStep step,
            Session session,
            SessionSentence sentence
    ) {}
}
