package com.capstoneecho.echo_back.pronunciation.phoneme.support;

import com.capstoneecho.echo_back.global.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

// 로컬 디스크 구현. app.storage.local-root 아래 phonemes/ 디렉터리에 음소당 한 파일로 둔다.
@Component
public class LocalPhonemeImageStorage implements PhonemeImageStorage {

    private static final String SUBDIR = "phonemes";

    private final Path root;

    public LocalPhonemeImageStorage(AppProperties properties) {
        String configuredRoot = properties.storage().localRoot();
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalStateException("app.storage.local-root must be configured");
        }
        this.root = Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    @Override
    public String save(String phoneme, String extension, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("image bytes are required");
        }
        String imagePath = SUBDIR + "/" + phoneme + "." + extension;
        Path absolute = resolve(imagePath);
        try {
            Files.createDirectories(absolute.getParent());
            Files.write(absolute, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to save phoneme image: " + imagePath, e);
        }
        return imagePath;
    }

    @Override
    public byte[] read(String imagePath) {
        try {
            return Files.readAllBytes(resolve(imagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read phoneme image: " + imagePath, e);
        }
    }

    @Override
    public void delete(String imagePath) {
        try {
            Files.deleteIfExists(resolve(imagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete phoneme image: " + imagePath, e);
        }
    }

    // 경로 조작(.. 등)으로 스토리지 루트를 벗어나는 입력을 막는다.
    private Path resolve(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("imagePath is required");
        }
        Path resolved = root.resolve(imagePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("imagePath escapes storage root: " + imagePath);
        }
        return resolved;
    }
}
