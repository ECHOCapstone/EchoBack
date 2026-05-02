package com.capstoneecho.echo_back.app.learning;

import com.capstoneecho.echo_back.app.script.Script;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 Script(학습 unit) 안의 순서 있는 단계. INTRO 단계는 안내문만,
// RECORD 단계는 사용자가 발음할 단어/문장을 함께 보유한다. 정답 음소는 도메인이 보관하지 않고
// 녹음 시점에 모델 서버 G2P 로 즉석 산출된다.
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

    public static LearningStep intro(Script script, int orderIndex, String prompt) {
        return create(script, orderIndex, StepKind.INTRO, prompt, null);
    }

    public static LearningStep record(Script script, int orderIndex, String prompt, String targetText) {
        return create(script, orderIndex, StepKind.RECORD, prompt, targetText);
    }

    private static LearningStep create(
            Script script,
            int orderIndex,
            StepKind kind,
            String prompt,
            String targetText
    ) {
        var step = new LearningStep();
        step.script = script;
        step.orderIndex = orderIndex;
        step.kind = kind;
        step.prompt = prompt;
        step.targetText = targetText;
        return step;
    }
}
