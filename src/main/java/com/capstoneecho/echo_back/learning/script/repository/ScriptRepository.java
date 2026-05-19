package com.capstoneecho.echo_back.learning.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.capstoneecho.echo_back.learning.script.entity.Script;
import com.capstoneecho.echo_back.statistics.stats.support.BadgePolicy;
public interface ScriptRepository extends JpaRepository<Script, Long> {

    // preset=true 인 모든 시드 챕터를 ID 순으로. 추천 학습 목록과 랭킹 화면의 unitTitle fallback 에서 사용한다.
    // primitive boolean preset 의 JPA 프로퍼티 이름은 'preset' 이라 메서드 이름도 By-Preset 으로 표기한다.
    List<Script> findByPresetTrueOrderByIdAsc();

    List<Script> findByTrack_IdOrderByChapterOrderAscIdAsc(Long trackId);

    // 트랙별 챕터 수만 필요한 트랙 목록 화면을 위한 카운트 전용 쿼리. N+1 회피.
    long countByTrack_Id(Long trackId);

    // 마스터 배지 대상 챕터만 추린 결과. masteryBadgeName 이 채워진 시드 챕터들이며,
    // BadgePolicy 가 한 사용자의 누적 피드백에 대해 챕터별 마스터 여부를 평가할 때의 입력이 된다.
    List<Script> findByPresetTrueAndMasteryBadgeNameIsNotNullOrderByIdAsc();
}
