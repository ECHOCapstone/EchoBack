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
import com.capstoneecho.echo_back.translation.dto.TranslationResponse;
import com.capstoneecho.echo_back.translation.service.TranslationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final TranslationService translationService;

    public ScriptService(
            ScriptRepository scriptRepository,
            LearningStepRepository learningStepRepository,
            RecommendedScriptSelector recommendedScriptSelector,
            StatsZoneProvider statsZoneProvider,
            RuntimeSettings settings,
            TranslationService translationService) {
        this.scriptRepository = scriptRepository;
        this.learningStepRepository = learningStepRepository;
        this.recommendedScriptSelector = recommendedScriptSelector;
        this.statsZoneProvider = statsZoneProvider;
        this.settings = settings;
        this.translationService = translationService;
    }

    // 클래스 기본은 readOnly 이지만 이 메서드는 TranslationService 의 캐시 저장 (쓰기) 을 호출하므로
    // 메서드 단위로 readOnly 를 해제한다 — 캐시 미스 시에만 실제 쓰기가 일어난다.
    @Transactional
    public ScriptDetailResponse getScript(Long scriptId) {
        Script script = scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
        List<LearningStep> steps = learningStepRepository.findByScript_IdOrderByIdAsc(scriptId);
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
        }
        return ScriptDetailResponse.of(script, steps, resolveTranslations(steps));
    }

    // 학습 단계의 targetText 들을 모아 한 번의 호출로 번역을 받아 onMap 형태로 돌려준다.
    // 캐시 hit 가 높아 두 번째 사용자부터는 거의 즉시. 외부 호출 실패 / 빈 결과는 빈 맵 → FE 가 자막 줄 생략.
    private Map<String, String> resolveTranslations(List<LearningStep> steps) {
        List<String> targets = new ArrayList<>();
        for (LearningStep step : steps) {
            String target = step.getTargetText();
            if (target != null && !target.isBlank() && !targets.contains(target)) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            return Map.of();
        }
        TranslationResponse translated = translationService.translate(targets);
        Map<String, String> out = new HashMap<>();
        for (TranslationResponse.Item item : translated.items()) {
            if (item.target() != null && !item.target().isBlank()) {
                out.put(item.source(), item.target());
            }
        }
        return out;
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
