package com.capstoneecho.echo_back.learning.script.service;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.config.StatsZoneProvider;
import com.capstoneecho.echo_back.global.settings.RuntimeSettings;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.learning.script.repository.LearningStepRepository;
import com.capstoneecho.echo_back.learning.script.repository.ScriptRepository;
import com.capstoneecho.echo_back.learning.script.support.RecommendedScriptSelector;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final LearningStepRepository learningStepRepository;
    private final RecommendedScriptSelector recommendedScriptSelector;
    private final StatsZoneProvider statsZoneProvider;
    private final RuntimeSettings settings;

    public ScriptService(
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            RecommendedScriptSelector recommendedScriptSelector,
            StatsZoneProvider statsZoneProvider,
            RuntimeSettings settings) {
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.recommendedScriptSelector = recommendedScriptSelector;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
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

    // 같은 userId × 같은 날짜 조합이면 같은 추천 셋이 나오도록 결정적 셔플을 쓴다.
    public List<ScriptSummaryResponse> recommendToday(Long userId) {
        List<Script> presets = scriptRepository.findByPresetTrueOrderByIdAsc();
        LocalDate today = LocalDate.now(statsZoneProvider.zone());
        List<Script> picked = recommendedScriptSelector.select(
                userId, today, presets, settings.dailyRecommended());
        return picked.stream()
                .map(ScriptSummaryResponse::from)
                .toList();
    }
}
