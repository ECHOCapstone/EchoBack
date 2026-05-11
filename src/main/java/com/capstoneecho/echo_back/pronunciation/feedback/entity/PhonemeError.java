package com.capstoneecho.echo_back.pronunciation.feedback.entity;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "op", nullable = false, length = 16)
    private PhonemeOp op;

    @Column(name = "canonical", length = 32)
    private String canonical;

    @Column(name = "perceived", length = 32)
    private String perceived;

    @Column(name = "canonical_index")
    private Integer canonicalIndex;

    private PhonemeError(PhonemeOp op, String canonical, String perceived, Integer canonicalIndex) {
        this.op = op;
        this.canonical = canonical;
        this.perceived = perceived;
        this.canonicalIndex = canonicalIndex;
    }

    static PhonemeError create(PhonemeOp op, String canonical, String perceived, Integer canonicalIndex) {
        if (op == null) {
            throw new IllegalArgumentException("op is required");
        }
        return new PhonemeError(op, canonical, perceived, canonicalIndex);
    }

    void attachTo(PronunciationFeedback parent) {
        this.feedback = parent;
    }
}
