package com.capstoneecho.echo_back.app.script;

import com.capstoneecho.echo_back.app.common.BusinessException;
import com.capstoneecho.echo_back.app.common.ErrorCode;
import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.learning.LearningStepRepository;
import com.capstoneecho.echo_back.app.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.app.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.app.script.Script;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class ScriptServiceImpl implements ScriptService {

    private final ScriptRepository scriptRepository;
    private final LearningStepRepository stepRepository;

    ScriptServiceImpl(ScriptRepository scriptRepository, LearningStepRepository stepRepository) {
        this.scriptRepository = scriptRepository;
        this.stepRepository = stepRepository;
    }

    @Override
    public List<ScriptSummaryResponse> getRecommendedToday() {
        return scriptRepository.findByPresetTrueOrderByIdAsc().stream()
                .map(ScriptSummaryResponse::from)
                .toList();
    }

    @Override
    public ScriptDetailResponse getDetail(Long scriptId) {
        var script = getEntity(scriptId);
        var steps = stepRepository.findByScript_IdOrderByOrderIndexAsc(scriptId);
        return ScriptDetailResponse.of(script, steps);
    }

    @Override
    public Script getEntity(Long scriptId) {
        return scriptRepository.findById(scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCRIPT_NOT_FOUND));
    }

    @Override
    public LearningStep getStep(Long scriptId, Long stepId) {
        return stepRepository.findByIdAndScript_Id(stepId, scriptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STEP_NOT_FOUND));
    }
}
