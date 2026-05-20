package com.capstoneecho.echo_back.learning.script.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.script.support.RecommendedScriptSelector;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScriptService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAILY_RECOMMENDED_COUNT = 3;

    private final ScriptRepository scriptRepository;
    private final LearningStepRepository learningStepRepository;
    private final RecommendedScriptSelector recommendedScriptSelector;

    public ScriptService(
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            RecommendedScriptSelector recommendedScriptSelector) {
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.recommendedScriptSelector = recommendedScriptSelector;
    }

    public ScriptDetailResponse getScript(Long scriptId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
        List<LearningStep> steps = learningStepRepository.findByScript_IdOrderByIdAsc(scriptId);
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
        }
        return ScriptDetailResponse.of(script, steps);
    }

    public List<ScriptSummaryResponse> recommendToday(Long userId) {
        List<Script> presets = scriptRepository.findByPresetTrueOrderByIdAsc();
        LocalDate today = LocalDate.now(KST);
        List<Script> picked = recommendedScriptSelector.select(
                userId, today, presets, DAILY_RECOMMENDED_COUNT);
        return picked.stream()
                .map(ScriptSummaryResponse::from)
                .toList();
    }
}
