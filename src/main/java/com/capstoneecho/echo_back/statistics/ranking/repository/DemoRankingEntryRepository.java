package com.capstoneecho.echo_back.statistics.ranking.repository;

import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoRankingEntryRepository extends JpaRepository<DemoRankingEntry, Long> {

    List<DemoRankingEntry> findAllByOrderByAccuracyDesc();
}
