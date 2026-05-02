package com.capstoneecho.echo_back.app.learning;

import com.capstoneecho.echo_back.app.script.Script;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 Script(학습 unit) 안의 순서 있는 단계. INTRO 단계는 안내문만,
// RECORD 단계는 사용자가 발음할 단어/문장과 그 canonical 음소 시퀀스를 보유한다.
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

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepKind kind;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt;

    // RECORD 단계에서 사용자가 발음해야 하는 단어/문장. INTRO 단계는 null.
    @Column(name = "target_text", columnDefinition = "TEXT")
    private String targetText;

    // 공백으로 분리된 canonical 음소 시퀀스. 모델 서버에 그대로 canonical 폼으로 전송된다.
    @Column(name = "canonical_phonemes", columnDefinition = "TEXT")
    private String canonicalPhonemes;

    public static LearningStep intro(Script script, int orderIndex, String prompt) {
        return create(script, orderIndex, StepKind.INTRO, prompt, null, null);
    }

    public static LearningStep record(
            Script script,
            int orderIndex,
            String prompt,
            String targetText,
            String canonicalPhonemes
    ) {
        return create(script, orderIndex, StepKind.RECORD, prompt, targetText, canonicalPhonemes);
    }

    private static LearningStep create(
            Script script,
            int orderIndex,
            StepKind kind,
            String prompt,
            String targetText,
            String canonicalPhonemes
    ) {
        var step = new LearningStep();
        step.script = script;
        step.orderIndex = orderIndex;
        step.kind = kind;
        step.prompt = prompt;
        step.targetText = targetText;
        step.canonicalPhonemes = canonicalPhonemes;
        return step;
    }
}
