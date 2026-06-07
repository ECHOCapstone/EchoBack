package com.capstoneecho.echo_back.learning.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "session_sentences",
        indexes = @Index(
                name = "ix_session_sentences_session",
                columnList = "session_id, sentence_index"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionSentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "sentence_index", nullable = false)
    private int sentenceIndex;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    // LlmCanonicalGenerator 가 만든 단어별 ARPABET 시퀀스 JSON.
    @Column(name = "canonical_cached_json", columnDefinition = "LONGTEXT")
    private String canonicalCachedJson;

    // 1 = legacy g2p (없음), 2 = LLM 생성본.
    @Column(name = "canonical_version", nullable = false)
    private int canonicalVersion = 1;

    private SessionSentence(Session session, int sentenceIndex, String text) {
        this.session = session;
        this.sentenceIndex = sentenceIndex;
        this.text = text;
    }

    public void applyCanonicalCache(String json) {
        this.canonicalCachedJson = (json == null || json.isBlank()) ? null : json;
        this.canonicalVersion = 2;
    }

    static SessionSentence of(Session session, int sentenceIndex, String text) {
        if (session == null) {
            throw new IllegalArgumentException("session is required");
        }
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
        return new SessionSentence(session, sentenceIndex, text);
    }
}
