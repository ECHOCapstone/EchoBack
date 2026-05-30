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
    @DisplayName("findAll 은 시드된 데모 랭킹 행을 유효한 값으로 반환한다")
    void findAllReturnsSeededEntries() {
        List<DemoRankingEntry> entries = repository.findAll();

        assertThat(entries).hasSizeBetween(5, 10);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getNickname()).isNotBlank();
            assertThat(entry.getAccuracy()).isBetween(0, 100);
        });
        assertThat(entries)
                .extracting(DemoRankingEntry::getAccuracy)
                .anyMatch(accuracy -> accuracy == 95);
    }
}
