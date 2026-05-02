package com.capstoneecho.echo_back.app.script;

import com.capstoneecho.echo_back.app.learning.LearningStep;
import com.capstoneecho.echo_back.app.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.app.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.app.script.Script;

import java.util.List;

// 스크립트 도메인의 외부 노출 인터페이스. Recording/Feedback 도메인이 이 추상화에만 의존한다.
public interface ScriptService {

    List<ScriptSummaryResponse> getRecommendedToday();

    ScriptDetailResponse getDetail(Long scriptId);

    Script getEntity(Long scriptId);

    LearningStep getStep(Long scriptId, Long stepId);
}
