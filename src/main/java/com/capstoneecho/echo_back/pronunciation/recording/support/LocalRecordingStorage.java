package com.capstoneecho.echo_back.pronunciation.recording.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocalRecordingStorage implements RecordingStorage {

    private static final DateTimeFormatter YEAR_MONTH =
            DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC);

    private final Path root;

    public LocalRecordingStorage(AppProperties properties) {
        String configuredRoot = properties.storage().localRoot();
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("app.storage.localRoot must be configured");
        }
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    @Override
    public String save(long userId, byte[] wavBytes) {
        if (wavBytes == null) {
            throw new IllegalArgumentException("wavBytes is required");
        }
        String yearMonth = YEAR_MONTH.format(Instant.now());
        String audioPath = userId + "/" + yearMonth + "/" + UUID.randomUUID() + ".wav";
        Path absolute = root.resolve(audioPath);
        try {
            Files.createDirectories(absolute.getParent());
            Files.write(absolute, wavBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save recording: " + audioPath, e);
        }
        return audioPath;
    }

    @Override
    public void delete(String audioPath) {
        Path absolute = resolve(audioPath);
        try {
            Files.deleteIfExists(absolute);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete recording: " + audioPath, e);
        }
    }

    @Override
    public byte[] read(String audioPath) {
        Path absolute = resolve(audioPath);
        try {
            return Files.readAllBytes(absolute);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read recording: " + audioPath, e);
        }
    }

    private Path resolve(String audioPath) {
        if (audioPath == null || audioPath.isBlank()) {
            throw new IllegalArgumentException("audioPath is required");
        }
        Path resolved = root.resolve(audioPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("audioPath escapes storage root: " + audioPath);
        }
        return resolved;
    }
}
