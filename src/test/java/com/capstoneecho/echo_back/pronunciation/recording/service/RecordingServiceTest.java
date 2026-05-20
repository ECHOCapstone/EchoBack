package com.capstoneecho.echo_back.pronunciation.recording.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.LlmClient;
import com.capstoneecho.echo_back.external.llm.LlmContext;
import com.capstoneecho.echo_back.external.llm.RecordingGuidance;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeError;
import com.capstoneecho.echo_back.external.modelserver.dto.AnalyzeResult;
import com.capstoneecho.echo_back.external.modelserver.dto.G2pResult;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.support.DefaultSentenceSplitter;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadRequest;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingUploadResponse;
import com.capstoneecho.echo_back.pronunciation.recording.dto.WrongWord;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import com.capstoneecho.echo_back.pronunciation.recording.support.RecordingStorage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class RecordingServiceTest {

    private static final byte[] VALID_WAV =
            new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private LearningStepRepository stepRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private ModelServerClient modelServerClient;

    @MockitoBean
    private LlmClient llmClient;

    @MockitoBean
    private RecordingStorage recordingStorage;

    @BeforeEach
    void setUp() {
        Mockito.reset(modelServerClient, llmClient, recordingStorage);
        when(modelServerClient.g2p(anyString()))
                .thenReturn(new G2pResult("HH AH L OW", List.of()));
        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(perfectAnalyzeResult());
        when(llmClient.summarizeRecording(any(LlmContext.class)))
                .thenReturn(new RecordingGuidance("발음을 더 또렷하게 따라 읽어 보세요.", List.of()));
        when(recordingStorage.save(anyLong(), any(byte[].class)))
                .thenReturn("u/202605/test.wav");
    }

    @Test
    @DisplayName("upload 는 g2p 를 analyze 보다 먼저 호출하고 canonical 을 그대로 전달한다")
    void uploadInvokesG2pBeforeAnalyzeWithCanonical() {
        Fixture f = seedScriptFlow("hello world");

        recordingService.upload(
                f.userId(),
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                VALID_WAV);

        InOrder inOrder = Mockito.inOrder(modelServerClient);
        inOrder.verify(modelServerClient).g2p("hello world");
        inOrder.verify(modelServerClient).analyze(any(byte[].class), eq("HH AH L OW"));
    }

    @Test
    @DisplayName("upload 는 빈/헤더 없는 audio 에 대해 AUDIO_DECODE_FAILED 를 던진다")
    void corruptAudioMapsToAudioDecodeFailed() {
        Fixture f = seedScriptFlow("hello");
        RecordingUploadRequest req =
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null);

        assertThatThrownBy(() -> recordingService.upload(f.userId(), req, new byte[0]))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.AUDIO_DECODE_FAILED);

        assertThatThrownBy(() ->
                recordingService.upload(f.userId(), req, new byte[] {'X', 'Y', 'Z', 'Z'}))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.AUDIO_DECODE_FAILED);
    }

    @Test
    @DisplayName("upload 는 타 사용자 session 접근 시 SESSION_NOT_FOUND 를 던진다 (#1)")
    void accessOtherUsersSessionReturnsNotFound() {
        User owner = saveUser("owner" + System.nanoTime());
        User stranger = saveUser("stranger" + System.nanoTime());

        Long[] ids = transactionTemplate.execute(status -> {
            Session session = sessionRepository.save(Session.create(owner, "Owned"));
            session.updateScript("Hello world.", new DefaultSentenceSplitter());
            sessionRepository.flush();
            return new Long[] {session.getId(), session.getSentences().get(0).getId()};
        });

        RecordingUploadRequest req =
                new RecordingUploadRequest(null, null, ids[0], ids[1]);

        assertThatThrownBy(() -> recordingService.upload(stranger.getId(), req, VALID_WAV))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("upload 는 storage save 직후 TransactionSynchronization 을 등록한다")
    void commitFailureRegistersStorageCleanup() {
        Fixture f = seedScriptFlow("hello");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        transactionTemplate.execute(status -> {
            recordingService.upload(
                    f.userId(),
                    new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                    VALID_WAV);
            status.setRollbackOnly();
            return null;
        });

        verify(recordingStorage, times(1)).save(anyLong(), any(byte[].class));
        verify(recordingStorage, atLeastOnce()).delete(captor.capture());
        assertThat(captor.getValue()).isEqualTo("u/202605/test.wav");
    }

    @Test
    @DisplayName("upload 는 storage save 이전 parent resolution 단계 실패 시 storage 를 건드리지 않는다 (#11)")
    void partialFailureCleansStorageBeforeCommit() {
        Fixture f = seedScriptFlow("hello");
        Long badUserId = 999_999L;
        RecordingUploadRequest req =
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null);

        assertThatThrownBy(() -> recordingService.upload(badUserId, req, VALID_WAV))
                .isInstanceOf(BusinessException.class);

        verify(recordingStorage, never()).save(anyLong(), any(byte[].class));
        verify(recordingStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("upload 는 트랜잭션 commit 실패 시 afterCompletion 으로 storage 를 정리한다 (#12)")
    void commitFailureCleansStorageViaSync() {
        Fixture f = seedScriptFlow("hello");

        transactionTemplate.execute(status -> {
            recordingService.upload(
                    f.userId(),
                    new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                    VALID_WAV);
            status.setRollbackOnly();
            return null;
        });

        verify(recordingStorage, atLeastOnce()).delete("u/202605/test.wav");
    }

    @Test
    @DisplayName("upload 응답의 wrongWords 는 errors 존재 시 채워지고, 없으면 빈 리스트다 (#15)")
    void wrongWordsArePopulatedWhenErrorsExist() {
        Fixture f = seedScriptFlow("water bottle");

        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(analyzeWithErrors());
        when(llmClient.summarizeRecording(any(LlmContext.class)))
                .thenReturn(new RecordingGuidance(
                        "ɔ 모음을 더 둥글게.", List.of(new WrongWord("water", 0))));

        RecordingUploadResponse withErrors = recordingService.upload(
                f.userId(),
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                VALID_WAV);

        assertThat(withErrors.wrongWords()).containsExactly(new WrongWord("water", 0));
        assertThat(withErrors.errors()).hasSize(1);
        assertThat(withErrors.guidanceKr()).isNotBlank();

        Recording persistedWithErrors = recordingRepository.findById(withErrors.id()).orElseThrow();
        assertThat(persistedWithErrors.getWrongWordsJson())
                .as("wrongWordsJson must serialize with FRONT_API_SPEC §12 shape")
                .contains("\"word\":\"water\"")
                .contains("\"index\":0");

        when(modelServerClient.analyze(any(byte[].class), anyString()))
                .thenReturn(perfectAnalyzeResult());
        when(llmClient.summarizeRecording(any(LlmContext.class)))
                .thenReturn(new RecordingGuidance("좋아요.", List.of()));

        RecordingUploadResponse perfect = recordingService.upload(
                f.userId(),
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                VALID_WAV);

        assertThat(perfect.wrongWords()).isEmpty();
        assertThat(perfect.errors()).isEmpty();

        Recording persistedPerfect = recordingRepository.findById(perfect.id()).orElseThrow();
        assertThat(persistedPerfect.getWrongWordsJson()).isNull();
    }

    @Test
    @DisplayName("upload (script-flow) 는 Recording 을 저장하고 scriptId/stepId 를 응답에 채운다")
    void uploadScriptFlowPersistsRecording() {
        Fixture f = seedScriptFlow("hello");

        RecordingUploadResponse response = recordingService.upload(
                f.userId(),
                new RecordingUploadRequest(f.scriptId(), f.stepId(), null, null),
                VALID_WAV);

        assertThat(response.id()).isNotNull();
        assertThat(response.scriptId()).isEqualTo(f.scriptId());
        assertThat(response.stepId()).isEqualTo(f.stepId());
        assertThat(response.sessionId()).isNull();
        assertThat(response.guidanceKr()).isNotBlank();

        Recording reloaded = recordingRepository.findById(response.id()).orElseThrow();
        assertThat(reloaded.getAudioPath()).isEqualTo("u/202605/test.wav");
        assertThat(reloaded.getTargetTextSnapshot()).isEqualTo("hello");
    }

    // ---------- helpers ----------

    private record Fixture(Long userId, Long scriptId, Long stepId) {}

    private Fixture seedScriptFlow(String targetText) {
        User user = saveUser("u" + System.nanoTime());
        Track track = trackRepository.save(Track.create("T", "d", 1));
        Script script = scriptRepository.save(
                Script.createChapter(track, 1, "Ch", "content", Difficulty.EASY, null, null));
        LearningStep step = stepRepository.save(LearningStep.record(script, "say", targetText));
        return new Fixture(user.getId(), script.getId(), step.getId());
    }

    private User saveUser(String localPart) {
        return userRepository.save(User.fromOAuth2(localPart + "@example.com", localPart));
    }

    private static AnalyzeResult perfectAnalyzeResult() {
        return new AnalyzeResult(
                List.of("HH", "AH", "L", "OW"),
                Optional.of(List.of("HH", "AH", "L", "OW")),
                List.of(0.9, 0.85, 0.8, 0.75),
                List.of(),
                List.of(),
                Optional.of(0.0),
                1.23);
    }

    private static AnalyzeResult analyzeWithErrors() {
        AnalyzeError err = new AnalyzeError("substitution", 1, "ʌ", "ɔ");
        return new AnalyzeResult(
                List.of("w", "ʌ", "t", "ɚ"),
                Optional.of(List.of("w", "ɔ", "t", "ɚ")),
                List.of(0.91, 0.62, 0.88, 0.79),
                List.of(),
                List.of(err),
                Optional.of(0.25),
                2.0);
    }
}
