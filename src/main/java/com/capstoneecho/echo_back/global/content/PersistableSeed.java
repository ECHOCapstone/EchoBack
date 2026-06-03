package com.capstoneecho.echo_back.global.content;

// 어드민의 "영구 저장 / 시드 재적용" 동작을 갖는 모든 도메인이 따르는 표준 인터페이스.
// 도메인별 구현체는 자신의 DB 상태를 외부 yaml/md 로 직렬화하고, 그 파일을 지워 공장 기본값으로
// 되돌리는 책임만 진다.
public interface PersistableSeed {

    String domain();

    void persistCurrentStateToFile();

    void resetToDefaults();

    SeedFileStatus fileStatus();
}
