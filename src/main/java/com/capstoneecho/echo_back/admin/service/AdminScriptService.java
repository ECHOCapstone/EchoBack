package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.AdminStepRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptCreateRequest;
import com.capstoneecho.echo_back.admin.dto.ScriptUpdateRequest;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalJson;
import com.capstoneecho.echo_back.external.llm.canonical.CanonicalResult;
import com.capstoneecho.echo_back.external.llm.canonical.LlmCanonicalGenerator;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.entity.StepKind;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.track.entity.Track;
import com.capstoneecho.echo_back.learning.track.repository.TrackRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 트랙 챕터 스크립트의 생성 / 수정 / 삭제. 스텝은 스크립트가 소유하므로 수정 시 통째로 교체한다.
// RECORD 스텝 저장 시 LlmCanonicalGenerator 로 canonical 을 동기 생성해 영속한다 — 실패하면
// 스크립트 저장 자체 실패 (silent fallback 금지).
@Service
@Transactional
public class AdminScriptService {

    private final ScriptRepository scriptRepository;
    private final LearningStepRepository learningStepRepository;
    private final TrackRepository trackRepository;
    private final LlmCanonicalGenerator canonicalGenerator;
    private final CanonicalJson canonicalJson;

    public AdminScriptService(
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            TrackRepository trackRepository,
            LlmCanonicalGenerator canonicalGenerator,
            CanonicalJson canonicalJson) {
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.trackRepository = trackRepository;
        this.canonicalGenerator = canonicalGenerator;
        this.canonicalJson = canonicalJson;
    }

    @Transactional(readOnly = true)
    public List<ScriptSummaryResponse> listByTrack(Long trackId) {
        if (!trackRepository.existsById(trackId)) {
            throw new BusinessException(ErrorCode.TRACK_NOT_FOUND);
        }
        return scriptRepository.findByTrack_IdOrderByChapterOrderAsc(trackId).stream()
                .map(ScriptSummaryResponse::from)
                .toList();
    }

    public ScriptDetailResponse create(ScriptCreateRequest request) {
        Track track = trackRepository.findById(request.trackId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRACK_NOT_FOUND));
        // 마지막 챕터 뒤에 붙인다 (1-based). 챕터 순서는 어드민이 손대지 않고 추가 순서를 따른다.
        int nextOrder = Math.toIntExact(
                scriptRepository.countByTrack_IdAndChapterOrderIsNotNull(track.getId())) + 1;
        Script script = scriptRepository.save(Script.createChapter(
                track, nextOrder, request.title(), request.content(),
                request.difficulty(), request.practiceWord(), request.masteryBadgeName()));
        List<LearningStep> steps = replaceSteps(script, request.steps());
        return ScriptDetailResponse.of(script, steps);
    }

    public ScriptDetailResponse update(Long scriptId, ScriptUpdateRequest request) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
        script.update(request.title(), request.content(), request.difficulty(),
                request.practiceWord(), request.masteryBadgeName());
        List<LearningStep> steps = replaceSteps(script, request.steps());
        return ScriptDetailResponse.of(script, steps);
    }

    // 녹음/피드백의 FK 는 ON DELETE SET NULL 이라 스크립트 삭제 시 자동으로 끊긴다.
    // 스텝은 NOT NULL FK 이므로 먼저 지운다.
    public void delete(Long scriptId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
        learningStepRepository.deleteByScript_Id(scriptId);
        scriptRepository.delete(script);
    }

    // 기존 스텝을 모두 지우고 요청 순서대로 다시 저장한다 (id 오름차순 = 학습 순서).
    // RECORD 스텝은 저장 시점에 canonical 을 함께 채운다.
    private List<LearningStep> replaceSteps(Script script, List<AdminStepRequest> requests) {
        learningStepRepository.deleteByScript_Id(script.getId());
        List<LearningStep> steps = requests.stream()
                .map(request -> toStep(script, request))
                .toList();
        return learningStepRepository.saveAll(steps);
    }

    private LearningStep toStep(Script script, AdminStepRequest request) {
        if (request.kind() == StepKind.RECORD
                && (request.targetText() == null || request.targetText().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "RECORD 단계는 targetText 가 필요합니다.");
        }
        if (request.kind() == StepKind.RECORD) {
            LearningStep step = LearningStep.record(script, request.prompt(), request.targetText());
            step.applyCanonical(buildCanonicalJson(request.targetText()));
            return step;
        }
        return LearningStep.intro(script, request.prompt());
    }

    private String buildCanonicalJson(String text) {
        CanonicalResult result = canonicalGenerator.generate(text);
        if (result == null || result.words().isEmpty()) {
            throw new BusinessException(ErrorCode.CANONICAL_GENERATION_FAILED,
                    "스크립트 canonical 생성 실패: " + text);
        }
        return canonicalJson.serialize(result.words());
    }
}
