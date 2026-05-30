package com.capstoneecho.echo_back.statistics.ranking.repository;

import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import org.springframework.data.jpa.repository.JpaRepository;

// 데모 랭킹 시드 행을 읽는다. RankingService 가 사용자 행과 합쳐 메모리에서 재정렬하므로
// 별도 정렬 쿼리 없이 findAll 만으로 충분하다.
public interface DemoRankingEntryRepository extends JpaRepository<DemoRankingEntry, Long> {
}
