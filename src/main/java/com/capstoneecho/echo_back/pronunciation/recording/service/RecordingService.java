package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalTargetType;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.external.llm.canonical.UserCanonicalLockService;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
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
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import com.capstoneecho.echo_back.pronunciation.recording.support.WavHeaderValidator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
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
    private final ModelServerClient modelServerClient;
    private final LlmClient llmClient;
    private final LlmCanonicalGenerator canonicalGenerator;
    private final UserCanonicalLockService canonicalLockService;
    private final ObjectMapper objectMapper;
    private final WavHeaderValidator wavHeaderValidator;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final RuntimeSettings settings;
    // 읽기/쓰기 트랜잭션을 명시적으로 분리해, 느린 모델 서버·LLM HTTP 호출 동안 DB 커넥션을 잡지 않게 한다.
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;
    // persist 단계에서 부모 엔티티를 다시 조회하지 않고 프록시로 INSERT 하기 위한 EntityManager.
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
            ModelServerClient modelServerClient,
            LlmClient llmClient,
            LlmCanonicalGenerator canonicalGenerator,
            UserCanonicalLockService canonicalLockService,
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
        this.modelServerClient = modelServerClient;
        this.llmClient = llmClient;
        this.canonicalGenerator = canonicalGenerator;
        this.canonicalLockService = canonicalLockService;
        this.objectMapper = objectMapper;
        this.wavHeaderValidator = wavHeaderValidator;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.settings = settings;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    // 녹음 1건 업로드 흐름. canonical 생성과 채점이 의미적으로 다른 일이므로 별도 LLM 호출로 분리한다.
    //   (1) READ TX     — 부모 매핑/검증 + 입력 데이터 + canonical lock 조회
    //   (2) MODEL HTTP  — /transcribe 호출해 perceived 만 받는다
    //   (3) CALL 1      — lock 이 있으면 그 값을 사용. 없으면 LlmCanonicalGenerator 로 canonical 생성
    //                      (이때 perceived 를 함께 넘겨 자연 연결 발음을 정답에 반영)
    //   (4) LOCK 영속화  — 첫 시도였다면 새 canonical 을 REQUIRES_NEW 로 저장
    //   (5) CALL 2      — LlmClient.stepFeedback 으로 alignment + score + 피드백
    //   (6) WRITE TX    — 오디오 디스크 저장 + Recording 영속화
    public RecordingUploadResponse upload(
            Long userId, RecordingUploadRequest request, byte[] audioBytes) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);
        Mode mode = detectMode(request);

        // (1) 읽기 단계: 부모 엔티티 검증 + 입력 데이터 + canonical lock 조회.
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
            CanonicalTargetType targetType = resolveTargetType(mode);
            Long targetIdForLock = resolveTargetIdForLock(mode, ids);
            List<CanonicalWord> locked = canonicalLockService
                    .findLocked(userId, targetType, targetIdForLock)
                    .orElse(List.of());
            return new PreparedInput(
                    targetText, chapterTitle, priorAttempts, ids, targetType, targetIdForLock, locked);
        });

        // (2) 모델 서버 /transcribe.
        TranscribeResult transcribe = modelServerClient.transcribe(audioBytes, "");

        // (3) Call 1 — canonical 결정. lock 이 있으면 그대로 사용, 없으면 perceived 와 함께 LLM 호출.
        List<CanonicalWord> canonicalWords;
        boolean newCanonical;
        if (!prepared.lockedCanonicalWords().isEmpty()) {
            canonicalWords = prepared.lockedCanonicalWords();
            newCanonical = false;
        } else {
            CanonicalResult generated =
                    canonicalGenerator.generate(prepared.targetText(), transcribe.perceived());
            canonicalWords = generated.words();
            newCanonical = true;
        }

        // (4) 첫 시도였다면 새 canonical 을 lock 에 저장. REQUIRES_NEW 라 이후 단계 롤백과 무관하게 보존.
        if (newCanonical) {
            canonicalLockService.lock(
                    userId, prepared.targetType(), prepared.targetIdForLock(), canonicalWords);
        }

        // (5) Call 2 — 채점 + 피드백. canonical 은 입력으로 전달.
        LlmStepContext context = new LlmStepContext(
                prepared.chapterTitle(),
                prepared.targetText(),
                canonicalWords,
                transcribe.perceived(),
                prepared.priorAttempts());
        LlmStepFeedback feedback = llmClient.stepFeedback(context);
        double stepScore = feedback.score();
        List<String> canonicalPhonemes = flattenCanonical(canonicalWords);

        // (6) 쓰기 단계: 짧은 트랜잭션 안에서 오디오 저장 + Recording 영속화 + 응답 조립.
        return writeTx.execute(status ->
                persist(mode, audioBytes, prepared, canonicalWords, canonicalPhonemes,
                        transcribe, stepScore, feedback));
    }

    // mode 를 lock 의 target_type 으로 매핑한다.
    private static CanonicalTargetType resolveTargetType(Mode mode) {
        return switch (mode) {
            case SCRIPT_FLOW -> CanonicalTargetType.STEP;
            case SESSION_SENTENCE -> CanonicalTargetType.SESSION_SENTENCE;
        };
    }

    // mode 별로 lock 의 target_id 로 쓸 자식 식별자 (step.id 또는 sentence.id).
    private static Long resolveTargetIdForLock(Mode mode, ParentIds ids) {
        return switch (mode) {
            case SCRIPT_FLOW -> ids.stepId();
            case SESSION_SENTENCE -> ids.sentenceId();
        };
    }

    // 오디오 디스크 저장 + Recording 영속화. 부모 엔티티는 EntityManager.getReference 로 프록시만 만들어
    // 추가 SELECT 없이 FK 만 세팅한다.
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
        // 통과 판정은 LLM 점수 임계만 본다 — RuntimeSettings.passThreshold 가 SSOT.
        // LLM 의 retryRecommended 는 별도 신호로 응답에 그대로 실어 보내, FE 가 약점 안내 등에 활용할 수 있게 한다.
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

    // canonicalWords 의 음소를 단어 순서대로 평탄화. 채점/스냅샷 직렬화에 쓰인다.
    private static List<String> flattenCanonical(List<CanonicalWord> words) {
        if (words == null || words.isEmpty()) {
            return List.of();
        }
        List<String> flat = new ArrayList<>();
        for (CanonicalWord w : words) {
            if (w.phonemes() != null) {
                flat.addAll(w.phonemes());
            }
        }
        return flat;
    }

    // READ TX 에서 추출해 이후 단계로 넘기는 순수 입력 데이터.
    private record PreparedInput(
            String targetText,
            String chapterTitle,
            List<PriorAttempt> priorAttempts,
            ParentIds parentIds,
            CanonicalTargetType targetType,
            Long targetIdForLock,
            List<CanonicalWord> lockedCanonicalWords) {}

    // persist 단계에서 EntityManager.getReference 의 입력으로 쓰는 부모 ID 묶음.
    private record ParentIds(Long userId, Long scriptId, Long stepId, Long sessionId, Long sentenceId) {}

    // 같은 부모 (script+step 또는 session+sentence) 의 이전 시도들을 오름차순으로 가져온 뒤,
    // 가장 최근 priorAttemptsCap 개만 잘라 LLM 입력을 적정 토큰량으로 유지한다.
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

    // wrongWords 직렬화 실패는 데이터 손상을 뜻하므로 삼키지 않고 그대로 전파한다.
    private String serializeWrongWords(LlmStepFeedback feedback) {
        if (feedback.wrongWords().isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(feedback.wrongWords());
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

    // 부모 엔티티는 EntityManager.getReference 로 프록시만 만들어 추가 SELECT 없이 FK 만 세팅한다.
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
