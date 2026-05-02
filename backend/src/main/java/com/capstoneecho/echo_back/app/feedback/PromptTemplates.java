package com.capstoneecho.echo_back.app.feedback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;

// LLM 프롬프트 directive 의 단일 보관소.
// src/main/resources/prompts.yaml 을 한 번 읽어 키별로 꺼내 쓸 수 있게 해 두고, 톤 조정이나
// 데이터셋 기반 새 프롬프트 실험은 yaml 파일만 손대면 된다.
@Component
class PromptTemplates {

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

    public String step()         { return get("step"); }
    public String unit()         { return get("unit"); }
    public String retry()        { return get("retry"); }
    public String practiceWord() { return get("practice-word"); }

    // 키가 없거나 값이 비면 부팅 자체를 멈추는 게 안전하다 — 빈 directive 로 LLM 을 부르면
    // 응답 형식이 깨져 흐름이 더 어색해진다.
    private String get(String key) {
        var value = templates.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("prompts.yaml 에 '" + key + "' directive 가 비어 있습니다.");
        }
        return value.toString().trim();
    }
}
