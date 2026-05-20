package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.AppProperties;
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
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
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
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final double passThreshold;
    private final int priorAttemptsCap;

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
            ScoringPolicy scoringPolicy,
            PriorAttemptAssembler priorAttemptAssembler,
            AppProperties appProperties) {
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
        this.priorAttemptAssembler = priorAttemptAssembler;
        AppProperties.Gamification g = appProperties.gamification();
        this.passThreshold = g == null ? 80.0 : g.passThreshold();
        this.priorAttemptsCap = g == null ? 10 : Math.max(1, g.priorAttemptsCap());
    }

    // 녹음 1건 업로드 흐름:
    // (1) WAV 검증 → 부모 (script-step 또는 session-sentence) 매핑 → 목표 텍스트 추출
    // (2) 모델 서버 g2p + analyze 호출
    // (3) 같은 step / sentence 의 이전 시도들을 priorAttempts 로 묶어 LLM 호출 (구조화 출력)
    // (4) 오디오 디스크 저장 + Recording 엔티티 저장 (errorsJson / wrongWordsJson / stepScore / guidanceKr 캐싱)
    // (5) 점수 임계와 LLM 판정을 합쳐 passed / retryRecommended 를 응답에 포함
    // 트랜잭션이 롤백되면 디스크에 쓴 오디오 파일도 같이 정리한다.
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

        Double stepScore = analyze.per() == null ? null : scoringPolicy.perToScore(analyze.per());
        List<Recording> priorRecordings = findPriorAttempts(userId, parents);
        LlmStepContext context = buildStepContext(
                parents, targetText, analyze, g2p, stepScore, priorRecordings);
        LlmStepFeedback feedback = llmClient.stepFeedback(context);

        String audioPath = recordingStorage.save(userId, audioBytes);
        registerStorageCleanupOnNonCommit(audioPath);

        Recording recording = buildRecording(mode, parents, audioPath, targetText);
        recording.applyAnalysisSnapshot(
                joinTokens(analyze.perceived()),
                joinTokens(analyze.canonicalOrEmpty()),
                joinDoubles(analyze.peakSoftmax()));
        recording.applyErrorsJson(serializeErrors(analyze.errors()));
        recording.applyWrongWordsJson(serializeWrongWords(feedback));
        recording.applyStepScore(stepScore);
        recording.applyGuidanceKr(feedback.guidanceKr());
        Recording saved = recordingRepository.save(recording);

        boolean passed = stepScore != null && stepScore >= passThreshold && !feedback.retryRecommended();
        return RecordingUploadResponse.fromUpload(
                saved,
                analyze.perceived(),
                analyze.canonicalOrEmpty(),
                analyze.peakSoftmax(),
                analyze.errors().stream().map(RecordingService::toErrorView).toList(),
                feedback,
                passed);
    }

    // 같은 부모 (script+step 또는 session+sentence) 의 이전 시도들을 오름차순으로 가져온 뒤,
    // 가장 최근 priorAttemptsCap 개만 잘라 LLM 입력을 적정 토큰량으로 유지한다.
    private List<Recording> findPriorAttempts(Long userId, ResolvedParents parents) {
        List<Recording> all = loadAllPriorAttempts(userId, parents);
        if (all.size() <= priorAttemptsCap) {
            return all;
        }
        return all.subList(all.size() - priorAttemptsCap, all.size());
    }

    private List<Recording> loadAllPriorAttempts(Long userId, ResolvedParents parents) {
        if (parents.step() != null && parents.script() != null) {
            return recordingRepository.findAllByUser_IdAndScript_IdAndStep_IdOrderByCreatedAtAsc(
                    userId, parents.script().getId(), parents.step().getId());
        }
        if (parents.sentence() != null && parents.session() != null) {
            return recordingRepository
                    .findAllByUser_IdAndSession_IdAndSessionSentence_IdOrderByCreatedAtAsc(
                            userId, parents.session().getId(), parents.sentence().getId());
        }
        return List.of();
    }

    // LLM 호출에 들어갈 컨텍스트. priorAttempts 가 채워지면 LLM 이 누적 학습 흐름을 인지한다.
    private LlmStepContext buildStepContext(
            ResolvedParents parents,
            String targetText,
            AnalyzeResult analyze,
            G2pResult g2p,
            Double stepScore,
            List<Recording> priorRecordings) {
        String chapterTitle = parents.script() == null ? "" : parents.script().getTitle();
        return new LlmStepContext(
                chapterTitle,
                targetText,
                analyze.perceived(),
                analyze.canonicalOrEmpty(),
                analyze.errors(),
                g2p.words(),
                pickWeakPhoneme(analyze.errors()),
                stepScore,
                priorAttemptAssembler.fromRecordings(priorRecordings));
    }

    private static String pickWeakPhoneme(List<AnalyzeError> errors) {
        if (errors == null) return null;
        for (AnalyzeError e : errors) {
            if (e.canonical() != null && !e.canonical().isBlank()) {
                return e.canonical();
            }
        }
        return null;
    }

    private String serializeWrongWords(LlmStepFeedback feedback) {
        if (feedback.wrongWords().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(feedback.wrongWords());
        } catch (RuntimeException ex) {
            log.warn("Failed to serialize wrongWords; persisting NULL", ex);
            return null;
        }
    }

    private String serializeErrors(List<AnalyzeError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (RuntimeException ex) {
            log.warn("Failed to serialize analyze errors; persisting NULL", ex);
            return null;
        }
    }

    private static String joinTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return null;
        return String.join(" ", tokens);
    }

    private static String joinDoubles(List<Double> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(' ');
            Double v = values.get(i);
            sb.append(v == null ? "0" : v.toString());
        }
        return sb.toString();
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
                            audioPath, ex);
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
