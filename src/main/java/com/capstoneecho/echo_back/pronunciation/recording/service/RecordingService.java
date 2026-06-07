package com.capstoneecho.echo_back.pronunciation.recording.service;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmStepContext;
import com.capstoneecho.echo_back.external.llm.LlmStepFeedback;
import com.capstoneecho.echo_back.external.llm.PriorAttempt;
import com.capstoneecho.echo_back.external.modelserver.AnalysisSnapshotFormat;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
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
import com.capstoneecho.echo_back.pronunciation.feedback.support.ScoringPolicy;
import com.capstoneecho.echo_back.pronunciation.feedback.support.WeakPhonemeAnalyzer;
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
    private final ObjectMapper objectMapper;
    private final WavHeaderValidator wavHeaderValidator;
    private final ScoringPolicy scoringPolicy;
    private final PriorAttemptAssembler priorAttemptAssembler;
    private final WeakPhonemeAnalyzer weakPhonemeAnalyzer;
    private final RuntimeSettings settings;
    // 읽기/쓰기 트랜잭션을 명시적으로 분리해, 느린 모델 서버·LLM HTTP 호출 동안 DB 커넥션을 잡지 않게 한다.
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

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
            WeakPhonemeAnalyzer weakPhonemeAnalyzer,
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
        this.objectMapper = objectMapper;
        this.wavHeaderValidator = wavHeaderValidator;
        this.scoringPolicy = scoringPolicy;
        this.priorAttemptAssembler = priorAttemptAssembler;
        this.weakPhonemeAnalyzer = weakPhonemeAnalyzer;
        this.settings = settings;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    // 녹음 1건 업로드 흐름. 느린 외부 호출이 DB 커넥션을 잡지 않도록 세 단계로 분리한다:
    //   (1) READ TX  — 부모 매핑/검증 + 목표 텍스트·이전 시도 등 LLM 입력 데이터를 트랜잭션 안에서 모두 추출
    //   (2) HTTP     — 트랜잭션 밖에서 모델 서버 g2p/analyze + LLM 호출 (커넥션 미점유)
    //   (3) WRITE TX — 오디오 디스크 저장 + Recording 영속화 (롤백 시 오디오도 정리)
    public RecordingUploadResponse upload(
            Long userId, RecordingUploadRequest request, byte[] audioBytes) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        wavHeaderValidator.require(audioBytes);
        Mode mode = detectMode(request);

        // (1) 읽기 단계: 연관관계 초기화까지 트랜잭션 안에서 끝내고, 이후 단계가 쓸 순수 데이터만 들고 나온다.
        PreparedInput prepared = readTx.execute(status -> {
            ResolvedParents parents = resolveParents(userId, request, mode);
            String targetText = resolveTargetText(parents);
            String chapterTitle = parents.script() == null ? "" : parents.script().getTitle();
            List<PriorAttempt> priorAttempts =
                    priorAttemptAssembler.fromRecordings(findPriorAttempts(userId, parents));
            return new PreparedInput(targetText, chapterTitle, priorAttempts);
        });

        // (2) 외부 호출 단계: DB 트랜잭션 밖에서 수행한다.
        G2pResult g2p = modelServerClient.g2p(prepared.targetText());
        String canonical = g2p.phonemes() == null ? "" : g2p.phonemes();
        AnalyzeResult analyze = modelServerClient.analyze(audioBytes, canonical);
        // op 별 가중 채점 — substitution/deletion 은 1.0, insertion 은 0.5 (기본).
        // 분모는 max(N_canonical, N_perceived) 로 정규화되어 per 가 항상 [0,1] 범위.
        Double stepScore = analyze.per() == null ? null : scoringPolicy.scoreFromAnalyze(analyze);
        LlmStepContext context = new LlmStepContext(
                prepared.chapterTitle(),
                prepared.targetText(),
                analyze.perceived(),
                analyze.canonicalOrEmpty(),
                analyze.errors(),
                g2p.words(),
                weakPhonemeAnalyzer.firstCanonical(analyze.errors()),
                stepScore,
                prepared.priorAttempts());
        LlmStepFeedback feedback = llmClient.stepFeedback(context);

        // (3) 쓰기 단계: 짧은 트랜잭션 안에서 오디오 저장 + Recording 영속화 + 응답 조립.
        return writeTx.execute(status ->
                persist(userId, request, mode, audioBytes, prepared.targetText(), g2p, analyze, stepScore, feedback));
    }

    // 오디오 디스크 저장 + Recording 영속화. 부모 엔티티는 이 트랜잭션에서 다시 조회해 관리 상태로 쓴다
    // (Recording 팩토리가 step.getScript() 와 script 의 참조 동일성을 요구하므로 같은 영속성 컨텍스트여야 한다).
    private RecordingUploadResponse persist(
            Long userId,
            RecordingUploadRequest request,
            Mode mode,
            byte[] audioBytes,
            String targetText,
            G2pResult g2p,
            AnalyzeResult analyze,
            Double stepScore,
            LlmStepFeedback feedback) {
        ResolvedParents parents = resolveParents(userId, request, mode);

        String audioPath = recordingStorage.save(userId, audioBytes);
        registerStorageCleanupOnNonCommit(audioPath);

        Recording recording = buildRecording(mode, parents, audioPath, targetText);
        recording.applyAnalysisSnapshot(
                AnalysisSnapshotFormat.joinTokens(analyze.perceived()),
                AnalysisSnapshotFormat.joinTokens(analyze.canonicalOrEmpty()),
                AnalysisSnapshotFormat.joinSoftmax(analyze.peakSoftmax()));
        recording.applyErrorsJson(priorAttemptAssembler.toErrorsJson(analyze.errors()));
        recording.applyWrongWordsJson(serializeWrongWords(feedback));
        recording.applyStepScore(stepScore);
        recording.applyGuidanceKr(feedback.guidanceKr());
        Recording saved = recordingRepository.save(recording);

        double passThreshold = settings.passThreshold();
        // 통과 판정은 점수 임계만 본다 — 점수 임계 (RuntimeSettings.passThreshold) 가 SSOT.
        // LLM 의 retryRecommended 는 별도 신호로 응답에 그대로 실어 보내, FE 가 약점 안내 등에 활용할 수 있게 한다.
        // 두 신호를 AND 로 묶으면 점수가 충분히 높아도 LLM 정성 판정 한 번으로 미통과가 되어
        // "75점이면 통과" 라는 학습자 기대와 어긋난다.
        boolean passed = stepScore != null && stepScore >= passThreshold;
        return RecordingUploadResponse.fromUpload(
                saved,
                analyze.perceived(),
                analyze.canonicalOrEmpty(),
                analyze.peakSoftmax(),
                analyze.errors().stream().map(RecordingService::toErrorView).toList(),
                feedback,
                passed,
                analyze.speechRate(),
                g2p.words(),
                passThreshold);
    }

    // READ TX 에서 추출해 이후 단계로 넘기는 순수 입력 데이터.
    private record PreparedInput(String targetText, String chapterTitle, List<PriorAttempt> priorAttempts) {}

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
