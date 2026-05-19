package com.capstoneecho.echo_back.statistics.ranking.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
public interface DemoRankingEntryRepository extends JpaRepository<DemoRankingEntry, Long> {

    // 랭킹 화면이 정확도 내림차순으로 노출되므로 조회 시점에 동일 정렬을 보장한다.
    List<DemoRankingEntry> findAllByOrderByAccuracyDesc();
}
