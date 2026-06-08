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

    // LlmCanonicalGenerator 가 만든 단어별 ARPABET 시퀀스 JSON. 모든 사용자가 같은 정답을 본다.
    // NULL = 부팅 backfill / lazy 호출 실패 → 채점 시점에 다시 시도.
    @Column(name = "canonical_cached_json", columnDefinition = "LONGTEXT")
    private String canonicalCachedJson;

    private SessionSentence(Session session, int sentenceIndex, String text) {
        this.session = session;
        this.sentenceIndex = sentenceIndex;
        this.text = text;
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

    // canonical JSON 본문을 영속화한다. 빈 입력은 컬럼을 NULL 로 유지.
    public void applyCanonical(String canonicalJson) {
        this.canonicalCachedJson = (canonicalJson == null || canonicalJson.isBlank())
                ? null : canonicalJson;
    }
}
