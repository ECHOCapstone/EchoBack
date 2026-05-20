package com.capstoneecho.echo_back.pronunciation.recording.support;

public interface RecordingStorage {

    String save(long userId, byte[] wavBytes);

    void delete(String audioPath);

    byte[] read(String audioPath);
}
