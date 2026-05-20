package com.capstoneecho.echo_back.external.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

// classpath:prompts/*.md 를 로드해 키 (파일명) → 본문 매핑으로 노출하는 단일 출처.
// 변수는 {{name}} 형태로 적고 render(key, vars) 가 단순 치환을 수행한다.
// 정의되지 않은 변수가 본문에 남아 있으면 빈 문자열로 치환되어 LLM 호출을 깨지 않는다.
@Component
public class PromptCatalog {

    private static final String LOCATION = "classpath:prompts/*.md";
    private static final String EMPTY_PLACEHOLDER_REGEX = "\\{\\{[a-zA-Z0-9_]+}}";

    private final Map<String, String> templates;

    public PromptCatalog() {
        this.templates = loadAll();
    }

    public String raw(String key) {
        String template = templates.get(key);
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

    private static Map<String, String> loadAll() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Resource[] files = resolver.getResources(LOCATION);
            for (Resource file : files) {
                String name = file.getFilename();
                if (name == null || !name.endsWith(".md")) {
                    continue;
                }
                String key = name.substring(0, name.length() - 3);
                out.put(key, readAll(file));
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to scan " + LOCATION, e);
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("no prompt template found at " + LOCATION);
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
