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

    // BadgePolicy 가 챕터 단위 마스터 배지 (master_<scriptId>) 를 동적 생성할 때 사용한다.
    // 어드민/시드가 masteryBadgeName 을 채운 시드 챕터만 반환된다.
    List<Script> listMasteryChapters();
}
