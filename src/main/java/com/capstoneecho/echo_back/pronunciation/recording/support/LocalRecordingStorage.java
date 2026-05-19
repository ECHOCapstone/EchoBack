package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

// 로컬 디스크 저장소. 사용자별 하위 디렉토리에 timestamped 파일명을 부여해 충돌을 피한다.
@Component
class LocalRecordingStorage implements RecordingStorage {

    private final Path rootDir;

    LocalRecordingStorage(AppProperties properties) {
        this.rootDir = Paths.get(properties.storage().recordingDir()).toAbsolutePath().normalize();
    }

    @Override
    public Stored save(Long userId, String originalFilename, byte[] data) {
        var ext = pickExtension(originalFilename);
        var filename = Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + ext;
        var dir = rootDir.resolve(String.valueOf(userId));
        try {
            Files.createDirectories(dir);
            var target = dir.resolve(filename);
            Files.write(target, data);
            return new Stored(target.toString(), originalFilename);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 저장 실패: " + e.getMessage());
        }
    }

    @Override
    public byte[] read(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "파일 읽기 실패: " + e.getMessage());
        }
    }

    private String pickExtension(String filename) {
        if (filename == null) {
            return ".wav";
        }
        var idx = filename.lastIndexOf('.');
        if (idx <= 0 || idx == filename.length() - 1) {
            return ".wav";
        }
        return filename.substring(idx).toLowerCase();
    }
}
