package com.capstoneecho.echo_back.statistics.ranking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@Sql("/sql/demo-ranking-seed.sql")
class DemoRankingEntryDataJpaTest {

    @Autowired
    private DemoRankingEntryRepository repository;

    @Test
    @DisplayName("findAllByOrderByAccuracyDesc 는 accuracy 내림차순으로 정렬된 시드 데이터를 반환")
    void findAllOrderedByAccuracyDesc() {
        List<DemoRankingEntry> entries = repository.findAllByOrderByAccuracyDesc();

        assertThat(entries).hasSizeBetween(5, 10);
        assertThat(entries)
                .extracting(DemoRankingEntry::getAccuracy)
                .isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getNickname()).isNotBlank();
            assertThat(entry.getAccuracy()).isBetween(0, 100);
        });
        assertThat(entries.get(0).getAccuracy()).isEqualTo(95);
    }
}
