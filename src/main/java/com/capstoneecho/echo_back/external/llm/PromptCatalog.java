package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.global.content.PersistableContentStore;
import com.capstoneecho.echo_back.global.content.PersistableSeed;
import com.capstoneecho.echo_back.global.content.SeedFileLocations;
import com.capstoneecho.echo_back.global.content.SeedFileStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

// classpath:content/prompts/*.md 를 로드해 키 (파일명) → 본문 매핑으로 노출하는 단일 출처.
// 변수는 {{name}} 형태로 적고 render(key, vars) 가 단순 치환을 수행한다.
// 정의되지 않은 변수가 본문에 남아 있으면 빈 문자열로 치환되어 LLM 호출을 깨지 않는다.
//
// classpath 본문은 공장 기본값이고, ${app.content.dir}/prompts/<key>.md 파일이 있으면
// 그 위에 덮어쓴다. 어드민 편집은 그 파일을 직접 쓰므로 재시작 후에도 유지되고, reset 은
// 파일을 지워 기본값으로 되돌린다.
@Component
public class PromptCatalog implements PersistableSeed {

    private static final String DOMAIN = "prompts";
    private static final String DEFAULTS_LOCATION = "classpath:content/prompts/*.md";
    private static final String EMPTY_PLACEHOLDER_REGEX = "\\{\\{[a-zA-Z0-9_]+}}";

    private static final Logger log = LoggerFactory.getLogger(PromptCatalog.class);

    // 편집본 파일이 놓이는 쓰기 가능 디렉터리. 없으면 첫 쓰기 때 생성한다.
    private final Path overrideDir;
    // 어드민 시드 상태(파일 존재 / 마지막 수정 시각) 노출용. 다른 도메인과 같은 출처를 쓴다.
    private final PersistableContentStore contentStore;
    // classpath 공장 기본 본문 (불변). reset 의 기준이자 "재정의됨" 판정의 비교 대상.
    private final Map<String, String> defaults;
    // 실제 사용 본문 = 기본값 + 파일 오버라이드. 편집 시 write-through 한다.
    private final Map<String, String> effective;
    // 부팅 시 디스크에 발견됐으나 알려진 키가 아닌 .md 파일들. 어드민 응답에 노출해 운영자가
    // 의도된 편집인지 확인할 수 있게 한다.
    private final List<String> unknownOverrideKeys;

    public PromptCatalog(
            @Value("${app.content.dir}") String contentDir,
            PersistableContentStore contentStore
    ) {
        this.overrideDir = Path.of(contentDir, "prompts");
        this.contentStore = contentStore;
        this.defaults = Map.copyOf(loadDefaults());
        this.effective = new ConcurrentHashMap<>(this.defaults);
        this.unknownOverrideKeys = List.copyOf(applyFileOverrides());
    }

    // 시작 시 디스크의 편집본을 덧씌운다. 디렉터리에 알려지지 않은 키의 .md 파일이 있으면 WARN 으로 남기고
    // 어드민 응답용 목록에 모은다 (apply 되지 않음을 운영자가 인지하도록).
    private List<String> applyFileOverrides() {
        List<String> unknown = new ArrayList<>();
        if (!Files.isDirectory(overrideDir)) {
            return unknown;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(overrideDir, "*.md")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                String key = name.substring(0, name.length() - 3);
                if (defaults.containsKey(key)) {
                    effective.put(key, readFile(file));
                } else {
                    log.warn("Unknown prompt override file ignored: {} (key={} not in defaults {})",
                            file, key, defaults.keySet());
                    unknown.add(key);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 편집본 디렉터리를 스캔할 수 없습니다: " + overrideDir, e);
        }
        return unknown;
    }

    // 어드민 응답에 사용. 알려지지 않아 적용되지 않은 파일 키 목록.
    public List<String> unknownOverrideKeys() {
        return unknownOverrideKeys;
    }

    public String raw(String key) {
        String template = effective.get(key);
        if (template == null) {
            throw new IllegalArgumentException("prompt key not found: " + key);
        }
        return template;
    }

    // 변수 치환 후 남은 미사용 placeholder 는 빈 문자열로 비워 LLM 입력을 깨끗하게 유지한다.
    public String render(String key, Map<String, String> vars) {
        String body = raw(key);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                body = body.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return body.replaceAll(EMPTY_PLACEHOLDER_REGEX, "");
    }

    // ----- 어드민 관리 -----

    // 모든 프롬프트의 현재 본문 + 재정의 여부 (키 오름차순).
    public List<PromptView> list() {
        return defaults.keySet().stream().sorted().map(this::view).toList();
    }

    public PromptView get(String key) {
        requireKnown(key);
        return view(key);
    }

    // 본문을 재정의하고 편집본 파일에 영속화한다.
    public PromptView override(String key, String content) {
        requireKnown(key);
        writeFile(key, content);
        effective.put(key, content);
        return view(key);
    }

    // 편집본 파일을 지우고 classpath 기본값으로 되돌린다.
    public PromptView reset(String key) {
        requireKnown(key);
        deleteFile(key);
        effective.put(key, defaults.get(key));
        return view(key);
    }

    // 모든 편집본 파일을 지우고 effective 본문을 한 번에 기본값으로 되돌린다.
    // 잘못된 편집을 일괄 롤백할 때 사용한다.
    public List<PromptView> resetAll() {
        for (String key : defaults.keySet()) {
            deleteFile(key);
            effective.put(key, defaults.get(key));
        }
        return list();
    }

    // 편집본 디렉터리의 영구 저장 상태. 디렉터리는 빈 상태로도 존재할 수 있으므로 단순 Files.exists 로
    // 판정하면 한 번도 override 한 적 없는 인스턴스도 "영구 저장본 있음" 으로 잘못 보고된다.
    // 실제 override 한 프롬프트가 하나라도 있을 때만 exists=true 로 표시한다.
    public SeedFileStatus seedStatus() {
        boolean anyOverridden = defaults.keySet().stream()
                .anyMatch(key -> !effective.get(key).equals(defaults.get(key)));
        if (!anyOverridden) {
            return new SeedFileStatus(false, null, overrideDir.toString());
        }
        return contentStore.statusOf(SeedFileLocations.PROMPTS_DIR);
    }

    // ----- PersistableSeed 표준 어댑터 -----

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public SeedFileStatus fileStatus() {
        return seedStatus();
    }

    @Override
    public void resetToDefaults() {
        resetAll();
    }

    private PromptView view(String key) {
        String content = effective.get(key);
        return new PromptView(key, content, !content.equals(defaults.get(key)));
    }

    private void requireKnown(String key) {
        if (!defaults.containsKey(key)) {
            throw new BusinessException(ErrorCode.PROMPT_NOT_FOUND);
        }
    }

    // 프롬프트 한 건의 현재 상태. overridden 이 true 면 기본값이 아닌 재정의 본문이다.
    public record PromptView(String key, String content, boolean overridden) {}

    // 저장/삭제는 PersistableContentStore 를 통해 다른 도메인 (tracks / settings) 과 같은
    // 안전망 (atomic rename + .bak 보존 + path 단위 lock) 을 받는다. 직접 Files.writeString 을
    // 호출하면 전원이 끊긴 사이 반쪽 파일이 남거나, 잘못 override 했을 때 복구할 .bak 가 없다.
    private void writeFile(String key, String content) {
        contentStore.writeText(SeedFileLocations.PROMPTS_DIR + "/" + key + ".md", content);
    }

    private void deleteFile(String key) {
        contentStore.delete(SeedFileLocations.PROMPTS_DIR + "/" + key + ".md");
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 편집본을 읽을 수 없습니다: " + file, e);
        }
    }

    private static Map<String, String> loadDefaults() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Resource[] files = resolver.getResources(DEFAULTS_LOCATION);
            for (Resource file : files) {
                String name = file.getFilename();
                if (name == null || !name.endsWith(".md")) {
                    continue;
                }
                String key = name.substring(0, name.length() - 3);
                out.put(key, readAll(file));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan " + DEFAULTS_LOCATION, e);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("no prompt template found at " + DEFAULTS_LOCATION);
        }
        return out;
    }

    private static String readAll(Resource file) {
        try {
            return new String(
                    FileCopyUtils.copyToByteArray(file.getInputStream()),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read prompt " + file.getDescription(), e);
        }
    }
}
