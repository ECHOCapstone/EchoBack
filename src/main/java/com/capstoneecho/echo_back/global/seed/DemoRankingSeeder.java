package com.capstoneecho.echo_back.global.seed;

import com.capstoneecho.echo_back.statistics.ranking.entity.DemoRankingEntry;
import com.capstoneecho.echo_back.statistics.ranking.repository.DemoRankingEntryRepository;
import java.io.IOException;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// 시연용 가짜 사용자 목록을 부팅 시 한 번 채워 넣는다. demo_ranking_entries 행이 이미 있으면
// 그대로 둔다 (멱등). 실제 사용자 데이터가 충분히 쌓이면 테이블만 비워 두면 자연히 끄지지 않는다.
// 트랙 시드와 책임이 다르므로 InitialDataLoader 와 별도 컴포넌트로 분리.
@Component
@Profile("!test")
public class DemoRankingSeeder implements ApplicationRunner {

    private final DemoRankingEntryRepository repository;
    private final ObjectMapper objectMapper;
    private final Resource demoRankingResource;

    public DemoRankingSeeder(
            DemoRankingEntryRepository repository,
            ObjectMapper objectMapper,
            @Value("classpath:seed/demo-ranking.json") Resource demoRankingResource
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.demoRankingResource = demoRankingResource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        var file = readJson(demoRankingResource, SeedData.DemoRankingFile.class);
        var entries = new ArrayList<DemoRankingEntry>(file.entries().size());
        for (var entry : file.entries()) {
            entries.add(DemoRankingEntry.of(entry.nickname(), entry.accuracy()));
        }
        repository.saveAll(entries);
    }

    // 시드 파일이 없거나 형식이 깨지면 부팅을 막는다. 잘못된 시드로 서비스를 띄우는 쪽이 더 위험하다.
    private <T> T readJson(Resource resource, Class<T> type) {
        try (var in = resource.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "시드 파일을 읽을 수 없습니다: " + resource.getDescription(), e);
        }
    }
}
