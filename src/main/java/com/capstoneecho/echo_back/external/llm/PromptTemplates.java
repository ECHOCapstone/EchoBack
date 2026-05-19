package com.capstoneecho.echo_back.external.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

// LLM 프롬프트 directive + 응답 스키마의 단일 보관소.
// src/main/resources/prompts.yaml 을 한 번 읽고 키별 prompt / schema 를 꺼내 쓸 수 있게 한다.
//
// 톤이나 데이터셋 기반 새 프롬프트 실험은 yaml 만 손대면 되고, schema 를 가진 키는 Gemini 가
// JSON 응답을 강제하므로 파싱이 안정적이다.
@Component
public class PromptTemplates {

    private final Map<String, Object> templates;

    PromptTemplates(@Value("classpath:prompts.yaml") Resource resource) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(resource);
        factory.afterPropertiesSet();
        var loaded = factory.getObject();
        if (loaded == null) {
            throw new IllegalStateException("prompts.yaml 을 읽을 수 없습니다: " + resource.getDescription());
        }
        this.templates = loaded;
    }

    // 모든 호출 앞에 prepend 되는 시스템 프롬프트 / 도메인 지식. 비어 있으면 빈 문자열.
    public String system() {
        var raw = templates.get("system");
        return raw == null ? "" : raw.toString().trim();
    }

    public String prompt(String key) {
        var entry = entry(key);
        var raw = entry.get("prompt");
        if (raw == null || raw.toString().isBlank()) {
            throw new IllegalStateException("prompts.yaml 의 '" + key + ".prompt' 가 비어 있습니다.");
        }
        return raw.toString().trim();
    }

    // schema 가 정의된 키만 JSON 강제 응답을 받는다. 자유 텍스트 키 (step / unit) 는 null.
    public Map<String, Object> schema(String key) {
        var entry = entry(key);
        var raw = entry.get("schema");
        if (raw == null) return null;
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalStateException("prompts.yaml 의 '" + key + ".schema' 형식이 잘못됐습니다.");
        }
        @SuppressWarnings("unchecked")
        var typed = (Map<String, Object>) map;
        return typed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> entry(String key) {
        var raw = templates.get(key);
        if (!(raw instanceof Map<?, ?>)) {
            throw new IllegalStateException("prompts.yaml 에 '" + key + "' 가 없거나 형식이 잘못됐습니다.");
        }
        return (Map<String, Object>) raw;
    }
}
