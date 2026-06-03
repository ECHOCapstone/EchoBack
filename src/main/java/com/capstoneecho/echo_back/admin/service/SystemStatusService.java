package com.capstoneecho.echo_back.admin.service;

import com.capstoneecho.echo_back.admin.dto.SystemStatusResponse;
import com.capstoneecho.echo_back.external.llm.GeminiLlmClient;
import com.capstoneecho.echo_back.external.modelserver.ModelServerClient;
import com.capstoneecho.echo_back.external.modelserver.dto.ModelCatalog;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Service;

// 어드민 대시보드용 시스템 상태 집계. 외부 의존성이 일시적으로 죽어 있어도 부분 결과를 돌려준다.
@Service
public class SystemStatusService {

    private final ModelServerClient modelServerClient;
    private final GeminiLlmClient gemini;
    private final Flyway flyway;
    private final List<PersistableSeed> seedDomains;

    public SystemStatusService(
            ModelServerClient modelServerClient,
            GeminiLlmClient gemini,
            Flyway flyway,
            List<PersistableSeed> seedDomains
    ) {
        this.modelServerClient = modelServerClient;
        this.gemini = gemini;
        this.flyway = flyway;
        this.seedDomains = seedDomains;
    }

    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                modelServerStatus(),
                llmStatus(),
                databaseStatus(),
                seedFileEntries()
        );
    }

    private SystemStatusResponse.ModelServer modelServerStatus() {
        try {
            ModelCatalog catalog = modelServerClient.models();
            return new SystemStatusResponse.ModelServer(true, catalog.active());
        } catch (BusinessException e) {
            return new SystemStatusResponse.ModelServer(false, null);
        }
    }

    private SystemStatusResponse.Llm llmStatus() {
        return new SystemStatusResponse.Llm(gemini.isAvailable());
    }

    // Flyway 가 적용한 모든 마이그레이션 중 success 인 마지막 항목을 추려 노출한다.
    private SystemStatusResponse.Database databaseStatus() {
        MigrationInfo[] all = flyway.info().applied();
        if (all == null || all.length == 0) {
            return new SystemStatusResponse.Database(null, null, 0);
        }
        MigrationInfo latest = java.util.Arrays.stream(all)
                .max(Comparator.comparingInt(m -> m.getInstalledRank() == null ? 0 : m.getInstalledRank()))
                .orElse(null);
        String version = latest == null || latest.getVersion() == null
                ? null : latest.getVersion().getVersion();
        Date installedOn = latest == null ? null : latest.getInstalledOn();
        Instant appliedAt = installedOn == null ? null : installedOn.toInstant();
        return new SystemStatusResponse.Database(version, appliedAt, all.length);
    }

    private List<SystemStatusResponse.SeedFileEntry> seedFileEntries() {
        List<SystemStatusResponse.SeedFileEntry> entries = new ArrayList<>(seedDomains.size());
        for (PersistableSeed seed : seedDomains) {
            entries.add(new SystemStatusResponse.SeedFileEntry(
                    seed.domain(), seed.supportsExplicitPersist(), seed.fileStatus()));
        }
        entries.sort(Comparator.comparing(SystemStatusResponse.SeedFileEntry::domain));
        return entries;
    }
}
