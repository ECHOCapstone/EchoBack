package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.external.modelserver.PhonemeRecognizer;
import com.capstoneecho.echo_back.external.modelserver.dto.TranscribeResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
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
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringService;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final PhonemeRecognizer phonemeRecognizer;
    private final LlmClient llmClient;
    private final LlmCanonicalGenerator canonicalGenerator;
    private final CanonicalJson canonicalJson;
    private final ScoringService scoringService;
    private final ObjectMapper objectMapper;
    private final WavHeaderValidator wavHeaderValidator;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final RuntimeSettings settings;
    // 읽기/쓰기 트랜잭션을 명시적으로 분리해, 느린 모델 서버·LLM HTTP 호출 동안 DB 커넥션을 잡지 않게 한다.
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    @PersistenceContext
    private EntityManager entityManager;

    public RecordingService(
            UserRepository userRepository,
            ScriptRepository scriptRepository,
            LearningStepRepository stepRepository,
            SessionRepository sessionRepository,
            SessionSentenceRepository sentenceRepository,
            RecordingRepository recordingRepository,
            RecordingStorage recordingStorage,
            PhonemeRecognizer phonemeRecognizer,
            LlmClient llmClient,
            LlmCanonicalGenerator canonicalGenerator,
            CanonicalJson canonicalJson,
            ScoringService scoringService,
            ObjectMapper objectMapper,
            WavHeaderValidator wavHeaderValidator,
            PriorAttemptAssembler priorAttemptAssembler,
            RuntimeSettings settings,
            PlatformTransactionManager txManager) {
        this.userRepository = userRepository;
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
        this.sessionRepository = sessionRepository;
        this.sentenceRepository = sentenceRepository;
        this.recordingRepository = recordingRepository;
        this.recordingStorage = recordingStorage;
        this.phonemeRecognizer = phonemeRecognizer;
        this.llmClient = llmClient;
        this.canonicalGenerator = canonicalGenerator;
        this.canonicalJson = canonicalJson;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
        this.wavHeaderValidator = wavHeaderValidator;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.settings = settings;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    // 녹음 1건 업로드 흐름:
    //   (1) READ TX     — 부모 매핑/검증 + 입력 데이터 + per-content canonical 캐시 조회
    //   (2) RECOGNIZE   — canonical 확보(캐시 없으면 즉시 생성) + /transcribe → perceived.
    //                     모델이 canonical 을 요구하지 않으면 transcribe 와 canonical 확보를 병렬 실행
    //   (3) CALL 2      — LlmClient.stepFeedback → alignment + 한국어 피드백
    //   (4) SCORING     — 백엔드 ScoringService.compute(alignment, errors) → 결정적 점수
    //   (5) WRITE TX    — 오디오 디스크 저장 + Recording 영속화
    public RecordingUploadResponse upload(
            Long userId, RecordingUploadRequest request, byte[] audioBytes) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);
        Mode mode = detectMode(request);

        PreparedInput prepared = readTx.execute(status -> {
            ResolvedParents parents = resolveParents(userId, request, mode);
            String targetText = resolveTargetText(parents);
            String chapterTitle = parents.script() == null ? "" : parents.script().getTitle();
            List<PriorAttempt> priorAttempts =
                    priorAttemptAssembler.fromRecordings(findPriorAttempts(userId, parents));
            ParentIds ids = new ParentIds(
                    userId,
                    parents.script() == null ? null : parents.script().getId(),
                    parents.step() == null ? null : parents.step().getId(),
                    parents.session() == null ? null : parents.session().getId(),
                    parents.sentence() == null ? null : parents.sentence().getId());
            String cachedJson = switch (mode) {
                case SCRIPT_FLOW -> parents.step().getCanonicalCachedJson();
                case SESSION_SENTENCE -> parents.sentence().getCanonicalCachedJson();
            };
            return new PreparedInput(targetText, chapterTitle, priorAttempts, ids, cachedJson);
        });

        // (2) 인식 + canonical 확보. canonical 을 요구하는 모델(FiLM)은 canonical 을 먼저 만들어 변조
        //     조건으로 주입하고, 그렇지 않은 모델은 /transcribe 를 canonical 확보와 병렬 실행한다.
        //     lazy canonical: 콘텐츠 캐시가 비면 resolveCanonical 이 즉시 생성해 콘텐츠에 채운다.
        PhonemeRecognizer.Recognized recognized =
                phonemeRecognizer.recognize(audioBytes, () -> resolveCanonical(mode, prepared));
        TranscribeResult transcribe = recognized.transcribe();
        List<CanonicalWord> canonicalWords = recognized.canonicalWords();
        List<String> canonicalPhonemes = recognized.canonicalPhonemes();

        // (3) Call 2 — 채점 + 피드백.
        LlmStepContext context = new LlmStepContext(
                prepared.chapterTitle(),
                prepared.targetText(),
                canonicalWords,
                transcribe.perceived(),
                prepared.priorAttempts());
        LlmStepFeedback feedback = llmClient.stepFeedback(context);

        // (4) 백엔드 결정적 점수.
        int stepScore = scoringService.compute(feedback.alignment(), feedback.errors());

        // (5) 쓰기 단계.
        return writeTx.execute(status ->
                persist(mode, audioBytes, prepared, canonicalWords, canonicalPhonemes,
                        transcribe, (double) stepScore, feedback));
    }

    // canonical 캐시를 읽고, 없으면 LLM 으로 만들어 콘텐츠 엔티티에 저장한 뒤 돌려준다.
    // 콘텐츠 저장은 REQUIRES_NEW 로 격리해 attempt 흐름 롤백과 무관하게 캐시는 살아남는다.
    private List<CanonicalWord> resolveCanonical(Mode mode, PreparedInput prepared) {
        List<CanonicalWord> cached = canonicalJson.deserialize(prepared.cachedCanonicalJson());
        if (!cached.isEmpty()) {
            return cached;
        }
        CanonicalResult generated = canonicalGenerator.generate(prepared.targetText());
        if (generated == null || generated.words().isEmpty()) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                    "canonical 생성 실패: " + prepared.targetText());
        }
        String json = canonicalJson.serialize(generated.words());
        persistCanonical(mode, prepared.parentIds(), json);
        return generated.words();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistCanonical(Mode mode, ParentIds ids, String json) {
        switch (mode) {
            case SCRIPT_FLOW -> {
                LearningStep step = stepRepository.findById(ids.stepId()).orElse(null);
                if (step == null) {
                    return;
                }
                step.applyCanonical(json);
                stepRepository.save(step);
            }
            case SESSION_SENTENCE -> {
                SessionSentence sentence = sentenceRepository.findById(ids.sentenceId()).orElse(null);
                if (sentence == null) {
                    return;
                }
                sentence.applyCanonical(json);
                sentenceRepository.save(sentence);
            }
        }
    }

    private RecordingUploadResponse persist(
            Mode mode,
            byte[] audioBytes,
            PreparedInput prepared,
            List<CanonicalWord> canonicalWords,
            List<String> canonicalPhonemes,
            TranscribeResult transcribe,
            Double stepScore,
            LlmStepFeedback feedback) {
        ParentIds ids = prepared.parentIds();

        String audioPath = recordingStorage.save(ids.userId(), audioBytes);
        registerStorageCleanupOnNonCommit(audioPath);

        Recording recording = buildRecording(mode, ids, audioPath, prepared.targetText());
        recording.applyAnalysisSnapshot(
                AnalysisSnapshotFormat.joinTokens(transcribe.perceived()),
                AnalysisSnapshotFormat.joinTokens(canonicalPhonemes),
                AnalysisSnapshotFormat.joinSoftmax(transcribe.peakSoftmax()));
        recording.applyErrorsJson(priorAttemptAssembler.toErrorsJson(feedback.errors()));
        recording.applyWrongWordsJson(serializeWrongWords(feedback));
        recording.applyStepScore(stepScore);
        recording.applyGuidanceKr(feedback.guidanceKr());
        Recording saved = recordingRepository.save(recording);

        double passThreshold = settings.passThreshold();
        boolean passed = stepScore != null && stepScore >= passThreshold;
        return RecordingUploadResponse.fromUpload(
                saved,
                transcribe.perceived(),
                canonicalPhonemes,
                transcribe.peakSoftmax(),
                feedback,
                passed,
                transcribe.speechRate(),
                canonicalWords,
                passThreshold);
    }

    private record PreparedInput(
            String targetText,
            String chapterTitle,
            List<PriorAttempt> priorAttempts,
            ParentIds parentIds,
            String cachedCanonicalJson) {}

    record ParentIds(Long userId, Long scriptId, Long stepId, Long sessionId, Long sentenceId) {}

    private List<Recording> findPriorAttempts(Long userId, ResolvedParents parents) {
        List<Recording> all = loadAllPriorAttempts(userId, parents);
        int cap = settings.priorAttemptsCap();
        if (all.size() <= cap) {
            return all;
        }
        return all.subList(all.size() - cap, all.size());
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

    private String serializeWrongWords(LlmStepFeedback feedback) {
        if (feedback.wrongWords().isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(feedback.wrongWords());
    }

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
            Mode mode, ParentIds ids, String audioPath, String targetText) {
        User user = entityManager.getReference(User.class, ids.userId());
        return switch (mode) {
            case SCRIPT_FLOW -> {
                Script script = entityManager.getReference(Script.class, ids.scriptId());
                LearningStep step = entityManager.getReference(LearningStep.class, ids.stepId());
                yield Recording.forScriptStepUnchecked(user, script, step, audioPath, targetText);
            }
            case SESSION_SENTENCE -> {
                Session session = entityManager.getReference(Session.class, ids.sessionId());
                SessionSentence sentence = entityManager.getReference(SessionSentence.class, ids.sentenceId());
                yield Recording.forSessionSentenceUnchecked(user, session, sentence, audioPath, targetText);
            }
        };
    }

    enum Mode {
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
