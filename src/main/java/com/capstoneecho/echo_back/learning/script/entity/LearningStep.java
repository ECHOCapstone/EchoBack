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
