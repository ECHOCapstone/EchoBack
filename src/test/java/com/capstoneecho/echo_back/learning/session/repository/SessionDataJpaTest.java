package com.capstoneecho.echo_back.learning.session.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.support.DataJpaMySqlTest;
import jakarta.persistence.EntityManager;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DataJpaMySqlTest
class SessionDataJpaTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("findByIdAndUser_Id 는 소유자 본인 세션만 반환한다")
    void findByIdAndUserScopedToOwner() {
        User owner = User.fromOAuth2("owner@example.com", "Owner");
        User other = User.fromOAuth2("other@example.com", "Other");
        em.persist(owner);
        em.persist(other);

        Session ownerSession = sessionRepository.saveAndFlush(Session.create(owner, "Owner Session"));

        Optional<Session> found = sessionRepository.findByIdAndUser_Id(ownerSession.getId(), owner.getId());
        Optional<Session> denied = sessionRepository.findByIdAndUser_Id(ownerSession.getId(), other.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Owner Session");
        assertThat(denied).isEmpty();
    }

    @Test
    @DisplayName("findByUser_IdOrderByFavoriteDescUpdatedAtDesc 는 favorite DESC, updatedAt DESC 순으로 정렬된다")
    void findByUserOrderedByFavoriteThenUpdatedAtDesc() throws InterruptedException {
        User user = User.fromOAuth2("carol@example.com", "Carol");
        em.persist(user);

        Session older = sessionRepository.saveAndFlush(Session.create(user, "Older"));
        Thread.sleep(25);
        Session newer = sessionRepository.saveAndFlush(Session.create(user, "Newer"));
        Thread.sleep(25);
        Session favored = Session.create(user, "Favored");
        favored.setFavorite(true);
        Session favoredSaved = sessionRepository.saveAndFlush(favored);

        List<Session> sessions = sessionRepository
                .findByUser_IdOrderByFavoriteDescUpdatedAtDesc(user.getId());

        assertThat(sessions).extracting(Session::getId)
                .containsExactly(favoredSaved.getId(), newer.getId(), older.getId());
    }

    @Test
    @DisplayName("deleteByIdAndUser_Id 는 타인 세션을 삭제하지 않고 영향 행 0 을 반환한다")
    void deleteByIdAndUserDoesNotAffectOtherUserSession() {
        User owner = User.fromOAuth2("dan@example.com", "Dan");
        User other = User.fromOAuth2("eve@example.com", "Eve");
        em.persist(owner);
        em.persist(other);
        Session session = sessionRepository.saveAndFlush(Session.create(owner, "Owned"));

        int strangerDeleted = sessionRepository.deleteByIdAndUser_Id(session.getId(), other.getId());
        em.flush();

        assertThat(strangerDeleted).isZero();
        assertThat(sessionRepository.findById(session.getId())).isPresent();

        int ownerDeleted = sessionRepository.deleteByIdAndUser_Id(session.getId(), owner.getId());
        em.flush();

        assertThat(ownerDeleted).isEqualTo(1);
        assertThat(sessionRepository.findById(session.getId())).isEmpty();
    }

    @Test
    @DisplayName("session_sentences 테이블에 ix_session_sentences_session 인덱스가 선언되어 있다")
    void sessionSentencesIndexDeclared() {
        Set<String> indexNames = new HashSet<>();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            DatabaseMetaData md = connection.getMetaData();
            for (String tableCandidate : new String[]{"session_sentences", "SESSION_SENTENCES"}) {
                try (ResultSet rs = md.getIndexInfo(null, null, tableCandidate, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null) {
                            indexNames.add(name.toLowerCase());
                        }
                    }
                }
            }
        });

        assertThat(indexNames).contains("ix_session_sentences_session");
    }
}
