package com.capstoneecho.echo_back.app.session;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Session 안의 한 학습 문장. SentenceSplitter 로 분할된 결과 한 조각이며,
// sentenceIndex 가 같은 세션 내 순서를 결정한다.
//
// Recording.sessionSentenceId 가 이 엔티티의 id 를 참조해 어떤 문장에 대한 녹음인지 식별한다.
@Entity
@Table(name = "session_sentences", indexes = @Index(name = "ix_session_sentences_session", columnList = "session_id, sentence_index"))
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

    @Column(columnDefinition = "TEXT", nullable = false)
    private String text;

    public static SessionSentence of(Session session, int sentenceIndex, String text) {
        var s = new SessionSentence();
        s.session = session;
        s.sentenceIndex = sentenceIndex;
        s.text = text;
        return s;
    }
}
