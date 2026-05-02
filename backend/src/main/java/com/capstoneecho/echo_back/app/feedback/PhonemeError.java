package com.capstoneecho.echo_back.app.feedback;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 한 PronunciationFeedback 안의 단일 오류 항목. op 는 substitution/insertion/deletion/correct.
@Entity
@Table(name = "phoneme_errors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhonemeError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feedback_id", nullable = false)
    private PronunciationFeedback feedback;

    @Column(length = 16, nullable = false)
    private String op;

    @Column(length = 32)
    private String canonical;

    @Column(length = 32)
    private String perceived;

    @Column(name = "canonical_index")
    private Integer canonicalIndex;

    public static PhonemeError of(String op, String canonical, String perceived, Integer canonicalIndex) {
        var e = new PhonemeError();
        e.op = op;
        e.canonical = canonical;
        e.perceived = perceived;
        e.canonicalIndex = canonicalIndex;
        return e;
    }

    void attachTo(PronunciationFeedback feedback) {
        this.feedback = feedback;
    }
}
