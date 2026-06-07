package com.capstoneecho.echo_back.learning.session.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import com.capstoneecho.echo_back.learning.session.support.SentenceSplitter;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.support.DataJpaMySqlTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaMySqlTest
class SessionUpdateScriptTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionSentenceRepository sessionSentenceRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("updateScript 가 sentences 컬렉션을 통째로 교체하고 이전 행을 orphan-remove 한다")
    void replacesSentencesAndDeletesOrphans() {
        User user = User.fromOAuth2("alice@example.com", "Alice");
        em.persist(user);

        Session session = Session.create(user, "My Session");
        SentenceSplitter firstSplitter = text -> List.of("Hello world.", "Practice English.");
        session.updateScript("Hello world. Practice English.", firstSplitter);
        sessionRepository.saveAndFlush(session);

        assertThat(sessionSentenceRepository.count()).isEqualTo(2L);

        SentenceSplitter secondSplitter = text -> List.of("Brand new content.");
        session.updateScript("Brand new content.", secondSplitter);
        sessionRepository.saveAndFlush(session);
        em.clear();

        Session reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScriptText()).isEqualTo("Brand new content.");
        assertThat(reloaded.getSentences())
                .extracting(SessionSentence::getText)
                .containsExactly("Brand new content.");
        assertThat(sessionSentenceRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Session.create 는 scriptText 빈 문자열 + favorite=false 로 초기화된다")
    void createInitializesScriptTextAndFavorite() {
        User user = User.fromOAuth2("bob@example.com", "Bob");
        em.persist(user);

        Session session = Session.create(user, "Fresh");
        sessionRepository.saveAndFlush(session);

        assertThat(session.getScriptText()).isEqualTo("");
        assertThat(session.isFavorite()).isFalse();
        assertThat(session.getSentences()).isEmpty();
    }
}
