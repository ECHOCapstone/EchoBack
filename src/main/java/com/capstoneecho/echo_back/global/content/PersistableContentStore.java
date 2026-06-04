package com.capstoneecho.echo_back.global.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 어드민이 "영구 저장(persist)" 액션을 누르면, 외부 콘텐츠 디렉터리(app.content.dir) 에 있는
// 시드 파일(tracks.yaml / prompts/<key>.md / settings-overrides.yaml 등) 을 새 본문으로 덮어쓴다.
//
// 안전망 두 가지:
//   1) atomic rename — 임시 파일에 먼저 쓰고 fsync 후 rename. 쓰는 도중 전원 끊겨도 옛 파일 또는
//      완전히 새 파일 둘 중 하나만 남는다 (부분 손상 X).
//   2) .bak 보존 — 덮어쓰기 직전 옛 파일을 <name>.bak 로 보존. 잘못 영구 저장했을 때 손수 복구 가능.
//
// 단일 인스턴스 가정 — 여러 인스턴스가 동시에 같은 디렉터리에 쓰는 락은 없다 (분산 락은 별도).
// 다만 같은 인스턴스 안의 동시 admin 액션 (두 탭에서 동시 저장, 더블클릭) 은 path 별 ReentrantLock 으로
// 직렬화한다. 락이 없으면 두 번째 호출의 backupIfPresent 가 첫 번째 호출이 막 쓴 새 본문을 .bak 로
// 덮어 옛 상태 복구가 영영 불가해진다.
@Component
public class PersistableContentStore {

    private static final Logger log = LoggerFactory.getLogger(PersistableContentStore.class);
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String TMP_SUFFIX = ".tmp";

    private final Path contentDir;
    // 같은 relativePath 의 write/delete 는 직렬화. 다른 path 는 병렬 허용해 처리량을 유지한다.
    private final ConcurrentMap<String, ReentrantLock> pathLocks = new ConcurrentHashMap<>();

    public PersistableContentStore(@Value("${app.content.dir}") String contentDir) {
        this.contentDir = Path.of(Objects.requireNonNull(contentDir, "contentDir"));
    }

    private ReentrantLock lockFor(String relativePath) {
        return pathLocks.computeIfAbsent(relativePath, k -> new ReentrantLock());
    }

    public Path resolve(String relativePath) {
        return contentDir.resolve(relativePath);
    }

    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    public SeedFileStatus statusOf(String relativePath) {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            return new SeedFileStatus(false, null, target.toString());
        }
        try {
            FileTime mtime = Files.getLastModifiedTime(target);
            return new SeedFileStatus(true, mtime.toInstant(), target.toString());
        } catch (IOException e) {
            return new SeedFileStatus(true, Instant.EPOCH, target.toString());
        }
    }

    // 텍스트 본문을 atomic rename + bak 보존으로 영구 저장한다. path 단위 락으로 동시 호출 직렬화.
    public void writeText(String relativePath, String content) {
        Objects.requireNonNull(content, "content");
        Path target = resolve(relativePath);
        ReentrantLock lock = lockFor(relativePath);
        lock.lock();
        try {
            Files.createDirectories(target.getParent());
            backupIfPresent(target);
            Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
            Files.writeString(tmp, content);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Persisted content file: {} ({} bytes)", target, content.length());
        } catch (IOException e) {
            throw new ContentPersistException("failed to persist " + target, e);
        } finally {
            lock.unlock();
        }
    }

    // 영구 저장된 파일을 지워 공장 기본값(classpath:content/...) 으로 되돌린다.
    // 미리 .bak 으로 보존하여 잘못 reset 했을 때 복구 가능하게 둔다. path 단위 락으로 직렬화.
    public boolean delete(String relativePath) {
        Path target = resolve(relativePath);
        ReentrantLock lock = lockFor(relativePath);
        lock.lock();
        try {
            if (!Files.exists(target)) {
                return false;
            }
            backupIfPresent(target);
            Files.deleteIfExists(target);
            log.info("Removed content file: {} (bak preserved)", target);
            return true;
        } catch (IOException e) {
            throw new ContentPersistException("failed to delete " + target, e);
        } finally {
            lock.unlock();
        }
    }

    private static void backupIfPresent(Path target) throws IOException {
        if (Files.exists(target)) {
            Path bak = target.resolveSibling(target.getFileName() + BACKUP_SUFFIX);
            Files.copy(target, bak, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static class ContentPersistException extends RuntimeException {
        public ContentPersistException(String message, Throwable cause) { super(message, cause); }
    }
}
