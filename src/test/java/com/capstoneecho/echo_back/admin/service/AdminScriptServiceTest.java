package com.capstoneecho.echo_back.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capstoneecho.echo_back.admin.dto.AdminStepRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptCreateRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptUpdateRequest;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalWord;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.entity.Difficulty;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// AdminScriptService 의 create / update / delete / list 흐름 + canonical 주입 동작 검증.
class AdminScriptServiceTest {

    private ScriptRepository scriptRepository;
    private LearningStepRepository stepRepository;
    private TrackRepository trackRepository;
    private LlmCanonicalGenerator canonicalGenerator;
    private CanonicalJson canonicalJson;
    private AdminScriptService service;

    @BeforeEach
    void setUp() {
        scriptRepository = mock(ScriptRepository.class);
        stepRepository = mock(LearningStepRepository.class);
        trackRepository = mock(TrackRepository.class);
        canonicalGenerator = mock(LlmCanonicalGenerator.class);
        canonicalJson = new CanonicalJson(new ObjectMapper());
        service = new AdminScriptService(
                scriptRepository, stepRepository, trackRepository, canonicalGenerator, canonicalJson);
        lenient().when(stepRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("listByTrack: 트랙이 존재하지 않으면 TRACK_NOT_FOUND")
    void listMissingTrackThrows() {
        when(trackRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listByTrack(99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TRACK_NOT_FOUND));
    }

    @Test
    @DisplayName("listByTrack: 트랙 존재 시 ScriptRepository 결과를 ScriptSummaryResponse 로 매핑")
    void listByTrackOk() {
        when(trackRepository.existsById(1L)).thenReturn(true);
        when(scriptRepository.findByTrack_IdOrderByChapterOrderAsc(1L)).thenReturn(List.of());

        assertThat(service.listByTrack(1L)).isEmpty();
    }

    @Test
    @DisplayName("create: 트랙 미존재 → TRACK_NOT_FOUND")
    void createMissingTrackThrows() {
        when(trackRepository.findById(9L)).thenReturn(Optional.empty());
        ScriptCreateRequest req = new ScriptCreateRequest(
                9L, "ch", "content", Difficulty.EASY, null, null,
                List.of(new AdminStepRequest(StepKind.INTRO, "p", null)));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.TRACK_NOT_FOUND));
    }

    @Test
    @DisplayName("create: RECORD 스텝에 canonical 자동 채워서 저장")
    void createInjectsCanonicalForRecordSteps() {
        Track track = Track.create("T", "d", 1);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(any())).thenReturn(0L);
        when(scriptRepository.save(any(Script.class))).thenAnswer(inv -> inv.getArgument(0));
        when(canonicalGenerator.generate("hello")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("hello", List.of("HH", "AH", "L", "OW")))));

        ScriptCreateRequest req = new ScriptCreateRequest(
                1L, "Ch", "content", Difficulty.EASY, null, null,
                List.of(
                        new AdminStepRequest(StepKind.INTRO, "안내", null),
                        new AdminStepRequest(StepKind.RECORD, "따라 읽기", "hello")));

        ScriptDetailResponse response = service.create(req);

        assertThat(response.title()).isEqualTo("Ch");
        verify(canonicalGenerator).generate("hello");
        verify(canonicalGenerator, never()).generate(eq("안내"));
        verify(stepRepository).deleteByScript_Id(any());
    }

    @Test
    @DisplayName("create: RECORD 스텝의 targetText 비어 있으면 INVALID_REQUEST")
    void createRejectsRecordWithoutTarget() {
        Track track = Track.create("T", "d", 1);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(any())).thenReturn(0L);
        when(scriptRepository.save(any(Script.class))).thenAnswer(inv -> inv.getArgument(0));

        ScriptCreateRequest req = new ScriptCreateRequest(
                1L, "Ch", "content", Difficulty.EASY, null, null,
                List.of(new AdminStepRequest(StepKind.RECORD, "say", "  ")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        verify(canonicalGenerator, never()).generate(anyString());
    }

    @Test
    @DisplayName("create: canonical generator 가 빈 결과를 돌려주면 CANONICAL_GENERATION_FAILED")
    void createFailsWhenCanonicalEmpty() {
        Track track = Track.create("T", "d", 1);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(any())).thenReturn(0L);
        when(scriptRepository.save(any(Script.class))).thenAnswer(inv -> inv.getArgument(0));
        when(canonicalGenerator.generate(anyString())).thenReturn(new CanonicalResult(List.of()));

        ScriptCreateRequest req = new ScriptCreateRequest(
                1L, "Ch", "content", Difficulty.EASY, null, null,
                List.of(new AdminStepRequest(StepKind.RECORD, "say", "hello")));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.CANONICAL_GENERATION_FAILED));
    }

    @Test
    @DisplayName("update: 스크립트 미존재 → SCRIPT_NOT_FOUND")
    void updateMissingScriptThrows() {
        when(scriptRepository.findById(99L)).thenReturn(Optional.empty());
        ScriptUpdateRequest req = new ScriptUpdateRequest(
                "T", "c", Difficulty.EASY, null, null,
                List.of(new AdminStepRequest(StepKind.INTRO, "p", null)));

        assertThatThrownBy(() -> service.update(99L, req))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SCRIPT_NOT_FOUND));
    }

    @Test
    @DisplayName("update: 기존 스텝을 모두 지우고 새 스텝으로 교체")
    void updateReplacesSteps() {
        Script script = scriptMock(11L);
        when(scriptRepository.findById(11L)).thenReturn(Optional.of(script));
        when(canonicalGenerator.generate("world")).thenReturn(new CanonicalResult(
                List.of(new CanonicalWord("world", List.of("W", "ER", "L", "D")))));

        ScriptUpdateRequest req = new ScriptUpdateRequest(
                "new", "new content", Difficulty.HARD, null, null,
                List.of(new AdminStepRequest(StepKind.RECORD, "read", "world")));

        service.update(11L, req);

        verify(script).update(eq("new"), eq("new content"), eq(Difficulty.HARD), any(), any());
        verify(stepRepository).deleteByScript_Id(11L);
        verify(stepRepository).saveAll(any());
    }

    @Test
    @DisplayName("delete: 스텝 먼저 지우고 스크립트 삭제")
    void deleteScript() {
        Script script = scriptMock(11L);
        when(scriptRepository.findById(11L)).thenReturn(Optional.of(script));

        service.delete(11L);

        verify(stepRepository).deleteByScript_Id(11L);
        verify(scriptRepository).delete(script);
    }

    @Test
    @DisplayName("delete: 스크립트 미존재 → SCRIPT_NOT_FOUND")
    void deleteMissingScript() {
        when(scriptRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class);
    }

    private static Script scriptMock(long id) {
        Script script = mock(Script.class);
        when(script.getId()).thenReturn(id);
        return script;
    }
}
