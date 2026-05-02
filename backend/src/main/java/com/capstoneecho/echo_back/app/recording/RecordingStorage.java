package com.capstoneecho.echo_back.app.recording;

// 녹음 파일의 영속화 추상화. 디스크/오브젝트 스토리지 등 구현체를 교체할 수 있다.
public interface RecordingStorage {

    Stored save(Long userId, String originalFilename, byte[] data);

    byte[] read(String path);

    record Stored(String path, String filename) {}
}
