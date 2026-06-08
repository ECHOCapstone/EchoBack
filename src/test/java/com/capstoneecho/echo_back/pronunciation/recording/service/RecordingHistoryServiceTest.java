package com.capstoneecho.echo_back.pronunciation.recording.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.external.llm.AlignmentOp;
import com.capstoneecho.echo_back.external.llm.LlmPhonemeError;
import com.capstoneecho.echo_back.external.llm.WrongWord;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.session.entity.SessionSentence;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.pronunciation.feedback.support.PriorAttemptAssembler;
import com.capstoneecho.echo_back.pronunciation.recording.dto.RecordingHistoryResponse;
import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.pronunciation.recording.repository.RecordingRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// RecordingHistoryService.forScript / forSession 의 read-side 정규화 흐름을 검증한다.
class RecordingHistoryServiceTest {

    private RecordingRepository recordingRepository;
    private ScriptRepository scriptRepository;
    private SessionRepository sessionRepository;
    private RuntimeSettings settings;
    private PriorAttemptAssembler priorAttemptAssembler;
    private ObjectMapper objectMapper;
    private RecordingHistoryService service;

    @BeforeEach
    void setUp() {
        recordingRepository = mock(RecordingRepository.class);
        scriptRepository = mock(ScriptRepository.class);
        sessionRepository = mock(SessionRepository.class);
        settings = mock(RuntimeSettings.class);
        priorAttemptAssembler = mock(PriorAttemptAssembler.class);
        objectMapper = new ObjectMapper();
        service = new RecordingHistoryService(
                recordingRepository, scriptRepository, sessionRepository,
                settings, priorAttemptAssembler, objectMapper);
        lenient().when(settings.passThreshold()).thenReturn(75.0);
        lenient().when(priorAttemptAssembler.parseErrors(any())).thenReturn(List.<LlmPhonemeError>of());
    }

    @Test
    @DisplayName("forScript: 스크립트가 없으면 SCRIPT_NOT_FOUND")
    void forScriptMissingThrows() {
        when(scriptRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.forScript(1L, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCRIPT_NOT_FOUND));
    }

    @Test
    @DisplayName("forSession: 세션이 없으면 SESSION_NOT_FOUND")
    void forSessionMissingThrows() {
        when(sessionRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.forSession(1L, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
    }

    @Test
    @DisplayName("forScript: 같은 step 의 여러 시도는 최신 한 건만 남는다 (LinkedHashMap 덮어쓰기)")
    void forScriptKeepsLatestPerStep() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording older = recordingForStep(101L, 11L, 60.0, Instant.parse("2026-01-01T00:00:00Z"));
        Recording newer = recordingForStep(102L, 11L, 85.0, Instant.parse("2026-01-01T00:10:00Z"));
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L))
                .thenReturn(List.of(older, newer));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.passThreshold()).isEqualTo(75.0);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).recordingId()).isEqualTo(102L);
        assertThat(response.items().get(0).stepScore()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("forScript: 서로 다른 step 은 모두 보존")
    void forScriptKeepsItemsForDifferentSteps() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording a = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        Recording b = recordingForStep(102L, 12L, 90.0, Instant.parse("2026-01-01T00:05:00Z"));
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L))
                .thenReturn(List.of(a, b));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(RecordingHistoryResponse.Item::stepId)
                .containsExactly(11L, 12L);
    }

    @Test
    @DisplayName("forSession: sentenceId 기반 키 추출 + 빈 stepKey 는 skip")
    void forSessionGroupsBySentenceId() {
        when(sessionRepository.existsById(2L)).thenReturn(true);
        Recording first = recordingForSentence(201L, 21L, 80.0, Instant.parse("2026-01-01T00:00:00Z"));
        Recording second = recordingForSentence(202L, 22L, 88.0, Instant.parse("2026-01-01T00:01:00Z"));
        Recording orphan = recordingForSentenceWithNullId(203L);
        when(recordingRepository.findAllSessionRecordingsWithSentence(1L, 2L))
                .thenReturn(List.of(first, second, orphan));

        RecordingHistoryResponse response = service.forSession(1L, 2L);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(RecordingHistoryResponse.Item::stepId)
                .containsExactly(21L, 22L);
    }

    @Test
    @DisplayName("forScript: perceived/canonical 공백 join 문자열을 토큰 리스트로 풀어낸다")
    void splitTokensRoundTrip() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording r = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        when(r.getPerceived()).thenReturn(" HH AH L  OW ");
        when(r.getCanonical()).thenReturn("HH AH L OW");
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L)).thenReturn(List.of(r));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items().get(0).perceived()).containsExactly("HH", "AH", "L", "OW");
        assertThat(response.items().get(0).canonical()).containsExactly("HH", "AH", "L", "OW");
    }

    @Test
    @DisplayName("forScript: perceived 가 null / blank 이면 빈 리스트")
    void splitTokensBlankFallback() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording r = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        when(r.getPerceived()).thenReturn(null);
        when(r.getCanonical()).thenReturn("   ");
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L)).thenReturn(List.of(r));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items().get(0).perceived()).isEmpty();
        assertThat(response.items().get(0).canonical()).isEmpty();
    }

    @Test
    @DisplayName("forScript: wrongWordsJson 정상 → 역직렬화, 손상 → 빈 리스트 폴백")
    void parseWrongWordsBranches() throws Exception {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording ok = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        when(ok.getWrongWordsJson()).thenReturn("[{\"word\":\"water\",\"index\":0}]");
        Recording broken = recordingForStep(102L, 12L, 70.0, Instant.parse("2026-01-01T00:05:00Z"));
        when(broken.getWrongWordsJson()).thenReturn("not-json");
        Recording blank = recordingForStep(103L, 13L, 70.0, Instant.parse("2026-01-01T00:10:00Z"));
        when(blank.getWrongWordsJson()).thenReturn("  ");
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L))
                .thenReturn(List.of(ok, broken, blank));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items().get(0).wrongWords())
                .containsExactly(new WrongWord("water", 0));
        assertThat(response.items().get(1).wrongWords()).isEmpty();
        assertThat(response.items().get(2).wrongWords()).isEmpty();
    }

    @Test
    @DisplayName("forScript: guidanceKr null 이면 빈 문자열로 노출")
    void guidanceNullBecomesEmpty() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording r = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        when(r.getGuidanceKr()).thenReturn(null);
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L)).thenReturn(List.of(r));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items().get(0).guidanceKr()).isEmpty();
    }

    @Test
    @DisplayName("errors 는 PriorAttemptAssembler.parseErrors 로 위임")
    void errorsDelegatedToAssembler() {
        when(scriptRepository.existsById(1L)).thenReturn(true);
        Recording r = recordingForStep(101L, 11L, 70.0, Instant.parse("2026-01-01T00:00:00Z"));
        when(r.getErrorsJson()).thenReturn("[]");
        when(priorAttemptAssembler.parseErrors("[]")).thenReturn(List.of(
                new LlmPhonemeError(AlignmentOp.ErrorType.SUBSTITUTION, "R", "L", 0)));
        when(recordingRepository.findAllChapterRecordingsWithStep(1L, 1L)).thenReturn(List.of(r));

        RecordingHistoryResponse response = service.forScript(1L, 1L);

        assertThat(response.items().get(0).errors()).hasSize(1);
    }

    private static Recording recordingForStep(long recordingId, long stepId,
                                              Double stepScore, Instant createdAt) {
        Recording rec = mock(Recording.class);
        LearningStep step = mock(LearningStep.class);
        when(step.getId()).thenReturn(stepId);
        when(rec.getId()).thenReturn(recordingId);
        when(rec.getStep()).thenReturn(step);
        when(rec.getStepScore()).thenReturn(stepScore);
        when(rec.getCreatedAt()).thenReturn(createdAt);
        when(rec.getGuidanceKr()).thenReturn("guide");
        return rec;
    }

    private static Recording recordingForSentence(long recordingId, long sentenceId,
                                                  Double stepScore, Instant createdAt) {
        Recording rec = mock(Recording.class);
        SessionSentence sentence = mock(SessionSentence.class);
        when(sentence.getId()).thenReturn(sentenceId);
        when(rec.getId()).thenReturn(recordingId);
        when(rec.getSessionSentence()).thenReturn(sentence);
        when(rec.getStepScore()).thenReturn(stepScore);
        when(rec.getCreatedAt()).thenReturn(createdAt);
        when(rec.getGuidanceKr()).thenReturn("guide");
        return rec;
    }

    private static Recording recordingForSentenceWithNullId(long recordingId) {
        Recording rec = mock(Recording.class);
        SessionSentence sentence = mock(SessionSentence.class);
        when(sentence.getId()).thenReturn(null);
        when(rec.getSessionSentence()).thenReturn(sentence);
        return rec;
    }
}
