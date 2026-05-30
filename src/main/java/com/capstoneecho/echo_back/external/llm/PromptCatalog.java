package com.capstoneecho.echo_back.external.llm;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class PromptCatalog {

    private static final String DEFAULTS_LOCATION = "classpath:content/prompts/*.md";
    private static final String EMPTY_PLACEHOLDER_REGEX = "\\{\\{[a-zA-Z0-9_]+}}";

    // 편집본 파일이 놓이는 쓰기 가능 디렉터리. 없으면 첫 쓰기 때 생성한다.
    private final Path overrideDir;
    // classpath 공장 기본 본문 (불변). reset 의 기준이자 "재정의됨" 판정의 비교 대상.
    private final Map<String, String> defaults;
    // 실제 사용 본문 = 기본값 + 파일 오버라이드. 편집 시 write-through 한다.
    private final Map<String, String> effective;

    public PromptCatalog(@Value("${app.content.dir}") String contentDir) {
        this.overrideDir = Path.of(contentDir, "prompts");
        this.defaults = Map.copyOf(loadDefaults());
        this.effective = new ConcurrentHashMap<>(this.defaults);
        applyFileOverrides();
    }

    // 시작 시 디스크의 편집본을 덧씌운다. 알 수 없는 키의 파일(삭제된 프롬프트 등)은 무시한다.
    private void applyFileOverrides() {
        for (String key : defaults.keySet()) {
            Path file = overrideDir.resolve(key + ".md");
            if (Files.isRegularFile(file)) {
                effective.put(key, readFile(file));
            }
        }
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

    private void writeFile(String key, String content) {
        try {
            Files.createDirectories(overrideDir);
            Files.writeString(overrideDir.resolve(key + ".md"), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 편집본을 저장할 수 없습니다: " + key, e);
        }
    }

    private void deleteFile(String key) {
        try {
            Files.deleteIfExists(overrideDir.resolve(key + ".md"));
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 편집본을 삭제할 수 없습니다: " + key, e);
        }
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
