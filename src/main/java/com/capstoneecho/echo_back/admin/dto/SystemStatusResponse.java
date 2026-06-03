package com.capstoneecho.echo_back.admin.dto;

import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import java.time.Instant;
import java.util.List;

// 어드민 시스템 상태 화면이 한 번에 받아오는 진단 묶음.
//   modelServer : 모델 서버 도달 여부 + 현재 활성 모델
//   llm         : LLM provider 의 현재 사용 가능 여부 (gemini api key 등)
//   database    : Flyway 가 적용한 가장 최근 마이그레이션 메타
//   seedFiles   : 각 PersistableSeed 도메인의 외부 영구 저장본 상태
public record SystemStatusResponse(
        ModelServer modelServer,
        Llm llm,
        Database database,
        List<SeedFileEntry> seedFiles
) {
    public record ModelServer(boolean reachable, String activeModel) {}

    public record Llm(boolean geminiAvailable) {}

    public record Database(String latestMigration, Instant appliedAt, int totalMigrations) {}

    public record SeedFileEntry(String domain, boolean supportsExplicitPersist, SeedFileStatus status) {}
}
