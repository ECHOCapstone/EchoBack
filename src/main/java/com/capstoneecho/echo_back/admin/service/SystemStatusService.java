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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

// 어드민 대시보드용 시스템 상태 집계. 외부 의존성이 일시적으로 죽어 있어도 부분 결과를 돌려준다.
@Service
public class SystemStatusService {

    private static final Logger log = LoggerFactory.getLogger(SystemStatusService.class);

    private final ModelServerClient modelServerClient;
    private final GeminiLlmClient gemini;
    // Flyway 는 prod/local 프로파일에만 존재한다(test/dev 는 H2 ddl-auto). 없을 때도 시스템 상태
    // endpoint 가 동작하도록 Optional 로 주입받는다.
    private final ObjectProvider<Flyway> flywayProvider;
    private final List<PersistableSeed> seedDomains;

    public SystemStatusService(
            ModelServerClient modelServerClient,
            GeminiLlmClient gemini,
            ObjectProvider<Flyway> flywayProvider,
            List<PersistableSeed> seedDomains
    ) {
        this.modelServerClient = modelServerClient;
        this.gemini = gemini;
        this.flywayProvider = flywayProvider;
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
    // Flyway 자체가 예외를 던지면 (DB 연결 끊김, history 테이블 손상 등) 시스템 상태 endpoint 전체가
    // 죽지 않도록 빈 결과로 폴백한다 — 운영자는 modelServer / llm / 시드 상태는 여전히 볼 수 있어야
    // 정확히 무엇이 망가졌는지 진단할 수 있다.
    private SystemStatusResponse.Database databaseStatus() {
        Flyway flyway = flywayProvider.getIfAvailable();
        if (flyway == null) {
            // Flyway 가 없는 프로파일(test/dev): 마이그레이션 정보 없이 빈 상태로 노출한다.
            return new SystemStatusResponse.Database(null, null, 0);
        }
        try {
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
        } catch (RuntimeException ex) {
            log.warn("Flyway 상태 조회 실패 — DB 상태를 빈 결과로 폴백합니다.", ex);
            return new SystemStatusResponse.Database(null, null, 0);
        }
    }

    private List<SystemStatusResponse.SeedFileEntry> seedFileEntries() {
        List<SystemStatusResponse.SeedFileEntry> entries = new ArrayList<>(seedDomains.size());
        for (PersistableSeed seed : seedDomains) {
            // 한 도메인의 fileStatus 가 IO 예외를 던져도 다른 도메인 상태는 보여줘야 한다.
            try {
                entries.add(new SystemStatusResponse.SeedFileEntry(
                        seed.domain(), seed.supportsExplicitPersist(), seed.fileStatus()));
            } catch (RuntimeException ex) {
                log.warn("시드 도메인 '{}' 상태 조회 실패 — 빈 상태로 폴백합니다.", seed.domain(), ex);
            }
        }
        entries.sort(Comparator.comparing(SystemStatusResponse.SeedFileEntry::domain));
        return entries;
    }

}
