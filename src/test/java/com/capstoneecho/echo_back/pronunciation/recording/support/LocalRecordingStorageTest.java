package com.capstoneecho.echo_back.pronunciation.recording.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRecordingStorageTest {

    @TempDir
    Path tempDir;

    private LocalRecordingStorage storage;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                null,
                null,
                null,
                null,
                new AppProperties.Storage(tempDir.toString()),
                null,
                null,
                null,
                null,
                null,
                null,
                null, null, null);
        storage = new LocalRecordingStorage(properties);
    }

    @Test
    @DisplayName("save → read 라운드트립으로 원본 바이트가 복원된다")
    void saveAndReadRoundTrip() {
        byte[] payload = "wav-bytes".getBytes(StandardCharsets.UTF_8);

        String audioPath = storage.save(7L, payload);

        assertThat(storage.read(audioPath)).isEqualTo(payload);
    }

    @Test
    @DisplayName("반환 경로는 userId/yyyyMM/uuid.wav 패턴을 따른다")
    void pathFollowsUserMonthUuidPattern() {
        String audioPath = storage.save(42L, new byte[]{1, 2, 3});

        String expectedMonth = DateTimeFormatter.ofPattern("yyyyMM")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        assertThat(audioPath)
                .matches("^42/\\d{6}/[0-9a-fA-F-]{36}\\.wav$")
                .startsWith("42/" + expectedMonth + "/");
        assertThat(Files.exists(tempDir.resolve(audioPath))).isTrue();
    }

    @Test
    @DisplayName("delete 는 존재하지 않는 경로에 대해서도 예외를 던지지 않는다")
    void deleteIsIdempotent() {
        assertThatCode(() -> storage.delete("99/202605/missing.wav"))
                .doesNotThrowAnyException();

        String audioPath = storage.save(5L, new byte[]{9});
        assertThatCode(() -> storage.delete(audioPath))
                .doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(audioPath))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("delete 후 read 는 예외를 던진다")
    void readAfterDeleteThrows() {
        String audioPath = storage.save(3L, new byte[]{9});
        storage.delete(audioPath);

        assertThatThrownBy(() -> storage.read(audioPath))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("동일 userId 로 여러 번 save 하면 UUID 가 모두 다르고 파일도 모두 생성된다")
    void multipleSavesProduceUniqueUuidFiles() throws IOException {
        Set<String> paths = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            paths.add(storage.save(11L, new byte[]{(byte) i}));
        }

        assertThat(paths).hasSize(5);

        String month = DateTimeFormatter.ofPattern("yyyyMM")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        try (Stream<Path> walk = Files.walk(tempDir.resolve("11").resolve(month))) {
            long fileCount = walk.filter(Files::isRegularFile).count();
            assertThat(fileCount).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("save 는 디렉터리를 자동 생성한다")
    void saveCreatesNestedDirectoriesAutomatically() {
        Path userDir = tempDir.resolve("123");
        assertThat(Files.exists(userDir)).isFalse();

        String audioPath = storage.save(123L, new byte[]{0});

        assertThat(audioPath).startsWith("123/");
        assertThat(Files.exists(tempDir.resolve(audioPath))).isTrue();
    }
}
