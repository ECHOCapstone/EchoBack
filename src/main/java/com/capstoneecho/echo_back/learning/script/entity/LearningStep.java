package com.capstoneecho.echo_back.learning.script.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 챕터 안의 학습 단계. INTRO 는 안내문, RECORD 는 사용자가 따라 읽을 목표 텍스트를 보유한다.
// PK 순서 = 시드 적재 순서 = 의도된 학습 순서이므로 별도 정렬 컬럼 없이 id 정렬로 안정적 출력을 보장한다.
@Entity
@Table(name = "learning_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LearningStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "script_id", nullable = false)
    private Script script;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private StepKind kind;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    // RECORD 단계에서 사용자가 따라 읽어야 할 목표 텍스트. INTRO 는 null.
    @Column(name = "target_text", columnDefinition = "TEXT")
    private String targetText;

    private LearningStep(Script script, StepKind kind, String prompt, String targetText) {
        this.script = script;
        this.kind = kind;
        this.prompt = prompt;
        this.targetText = targetText;
    }

    public static LearningStep intro(Script script, String prompt) {
        requireScript(script);
        requireNonBlank(prompt, "prompt");
        return new LearningStep(script, StepKind.INTRO, prompt, null);
    }

    public static LearningStep record(Script script, String prompt, String targetText) {
        requireScript(script);
        requireNonBlank(prompt, "prompt");
        requireNonBlank(targetText, "targetText");
        return new LearningStep(script, StepKind.RECORD, prompt, targetText);
    }

    private static void requireScript(Script script) {
        if (script == null) {
            throw new IllegalArgumentException("script is required");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
