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

// Session 안의 한 학습 문장. SentenceSplitter 로 분할된 결과 한 조각이며,
// sentenceIndex 가 같은 세션 안에서 순서를 결정한다.
// 외부 코드는 Session.updateScript 를 통해서만 생성되도록 정적 팩토리를 패키지 가시성으로 한정한다.
@Entity
@Table(
        name = "session_sentences",
        indexes = @Index(name = "ix_session_sentences_session", columnList = "session_id, sentence_index")
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
}
