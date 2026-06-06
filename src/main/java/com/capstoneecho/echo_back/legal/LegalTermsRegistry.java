package com.capstoneecho.echo_back.legal;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

// 약관 본문 + 버전의 단일 출처. 다른 영구 저장 도메인(PromptCatalog 등)과 동일한 패턴으로 동작한다.
//   - 공장 기본값(factoryDefaults): classpath:content/terms/{kind}-{version}.md
//   - 영구 저장본:                   ${app.content.dir}/terms/{kind}-{version}.md (있으면 우선)
//   - effective:                     factoryDefaults 위에 영구 저장본을 덧씌운 런타임 상태.
// 어드민의 PUT 호출은 effective 만 갱신하고, 영구 저장 누를 때 외부 파일로 dump 된다.
@Component
public class LegalTermsRegistry implements PersistableSeed {

    private static final String DOMAIN = "terms";

    private final String version;
    private final Map<TermsKind, String> factoryDefaults = new EnumMap<>(TermsKind.class);
    private final ConcurrentHashMap<TermsKind, String> effective = new ConcurrentHashMap<>();
    private final PersistableContentStore contentStore;

    public LegalTermsRegistry(AppProperties appProperties, PersistableContentStore contentStore) {
        AppProperties.Legal legal = appProperties.legal();
        if (legal == null || legal.termsVersion() == null || legal.termsVersion().isBlank()) {
            throw new IllegalStateException("app.legal.terms-version must be configured");
        }
        this.version = legal.termsVersion();
        this.contentStore = contentStore;
    }

    @PostConstruct
    void load() {
        for (TermsKind kind : TermsKind.values()) {
            String body = readClasspath(kind);
            factoryDefaults.put(kind, body);
            effective.put(kind, body);
        }
        applyFileOverrides();
    }

    public String version() {
        return version;
    }

    public String body(TermsKind kind) {
        return Objects.requireNonNull(effective.get(kind), () -> "terms body missing: " + kind);
    }

    // 어드민 화면이 한 번에 받아가는 스냅샷 — 종류별 본문 + override 여부.
    public Map<TermsKind, View> snapshot() {
        Map<TermsKind, View> out = new LinkedHashMap<>();
        for (TermsKind kind : TermsKind.values()) {
            String body = effective.get(kind);
            out.put(kind, new View(body, !body.equals(factoryDefaults.get(kind))));
        }
        return Collections.unmodifiableMap(out);
    }

    // 본문을 in-memory 에 덮어쓰고 외부 영구 저장본 파일에 기록한다.
    public View override(TermsKind kind, String body) {
        Objects.requireNonNull(kind, "kind");
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body is required");
        }
        contentStore.writeText(filePath(kind), body);
        effective.put(kind, body);
        return new View(body, !body.equals(factoryDefaults.get(kind)));
    }

    // 특정 종류의 영구 저장본을 지워 그 종류만 공장 기본값으로 되돌린다.
    public View resetSingle(TermsKind kind) {
        Objects.requireNonNull(kind, "kind");
        contentStore.delete(filePath(kind));
        String body = factoryDefaults.get(kind);
        effective.put(kind, body);
        return new View(body, false);
    }

    // ----- PersistableSeed -----

    @Override
    public String domain() {
        return DOMAIN;
    }

    // override 시점에 이미 파일이 기록되므로 별도 일괄 persist 액션은 의미가 없다.
    @Override
    public boolean supportsExplicitPersist() {
        return false;
    }

    @Override
    public void resetToDefaults() {
        for (TermsKind kind : TermsKind.values()) {
            contentStore.delete(filePath(kind));
            effective.put(kind, factoryDefaults.get(kind));
        }
    }

    @Override
    public SeedFileStatus fileStatus() {
        // 어느 한 종류라도 override 되어 있으면 디렉터리 상태를 그대로, 아니면 미존재로 보고한다.
        boolean anyOverridden = factoryDefaults.keySet().stream()
                .anyMatch(k -> !effective.get(k).equals(factoryDefaults.get(k)));
        if (!anyOverridden) {
            return new SeedFileStatus(false, null, contentStore.resolve(SeedFileLocations.TERMS_DIR).toString());
        }
        return contentStore.statusOf(SeedFileLocations.TERMS_DIR);
    }

    // ----- helpers -----

    private void applyFileOverrides() {
        for (TermsKind kind : TermsKind.values()) {
            Path file = contentStore.resolve(filePath(kind));
            if (Files.isRegularFile(file)) {
                effective.put(kind, readFile(file));
            }
        }
    }

    private String filePath(TermsKind kind) {
        return SeedFileLocations.TERMS_DIR + "/" + kind.fileSlug() + "-" + version + ".md";
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("약관 영구 저장본을 읽을 수 없습니다: " + file, e);
        }
    }

    private String readClasspath(TermsKind kind) {
        String location = "content/terms/" + kind.fileSlug() + "-" + version + ".md";
        ClassPathResource resource = new ClassPathResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("약관 본문 파일이 없습니다: " + location);
        }
        try {
            return new String(
                    FileCopyUtils.copyToByteArray(resource.getInputStream()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("약관 본문을 읽을 수 없습니다: " + location, e);
        }
    }

    // 한 종류 약관의 현재 상태. overridden 이 true 면 공장 기본값이 아닌 어드민 편집본이 사용 중이다.
    public record View(String body, boolean overridden) {}
}
