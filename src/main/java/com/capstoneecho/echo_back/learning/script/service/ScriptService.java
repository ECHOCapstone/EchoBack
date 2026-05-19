package com.capstoneecho.echo_back.learning.script.service;

import com.capstoneecho.echo_back.learning.script.entity.LearningStep;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.entity.Script;

import java.util.List;

import com.capstoneecho.echo_back.pronunciation.recording.entity.Recording;
import com.capstoneecho.echo_back.statistics.stats.support.BadgePolicy;
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
