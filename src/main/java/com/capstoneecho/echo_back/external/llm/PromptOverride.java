package com.capstoneecho.echo_back.external.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 프롬프트 키별 본문 오버라이드 한 건. classpath 기본 프롬프트를 덮어쓰는 런타임 값을 보관한다.
@Entity
@Table(name = "prompt_override")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromptOverride {

    @Id
    @Column(name = "prompt_key", length = 100)
    private String promptKey;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PromptOverride(String promptKey, String content) {
        this.promptKey = promptKey;
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public static PromptOverride of(String promptKey, String content) {
        return new PromptOverride(promptKey, content);
    }

    public void updateContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }
}
