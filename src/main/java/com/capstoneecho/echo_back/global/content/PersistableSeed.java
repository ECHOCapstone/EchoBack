package com.capstoneecho.echo_back.global.content;

// 어드민의 "영구 저장 / 시드 재적용" 동작을 갖는 모든 도메인이 따르는 표준 인터페이스.
// 각 도메인이 자신의 DB 상태를 외부 yaml/md 로 직렬화하고, 그 파일을 지워 공장 기본값으로
// 되돌리는 책임만 진다.
//
// 일부 도메인(예: Prompt) 은 키별 수정이 곧 파일 영구화라 명시적 persistCurrentStateToFile()
// 이 의미가 약하다. 그런 경우 supportsExplicitPersist() = false 를 유지해 어드민 UI 가
// "영구 저장" 버튼 자체를 숨기게 한다.
public interface PersistableSeed {

    String domain();

    SeedFileStatus fileStatus();

    void resetToDefaults();

    default boolean supportsExplicitPersist() {
        return false;
    }

    default void persistCurrentStateToFile() {
        throw new UnsupportedOperationException(
                domain() + " 는 명시적 영구 저장이 필요 없습니다 (수정이 곧 영구화).");
    }
}
