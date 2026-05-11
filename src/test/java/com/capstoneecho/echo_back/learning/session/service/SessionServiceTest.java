package com.capstoneecho.echo_back.learning.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.global.common.BusinessException;
import com.capstoneecho.echo_back.global.common.ErrorCode;
import com.capstoneecho.echo_back.learning.session.dto.SessionCreateRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionDetailResponse;
import com.capstoneecho.echo_back.learning.session.dto.SessionPatchRequest;
import com.capstoneecho.echo_back.learning.session.dto.SessionSummaryResponse;
import com.capstoneecho.echo_back.learning.session.entity.Session;
import com.capstoneecho.echo_back.learning.session.repository.SessionRepository;
import com.capstoneecho.echo_back.learning.session.repository.SessionSentenceRepository;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SessionServiceTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionSentenceRepository sessionSentenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private User newUser(String localPart) {
        User user = User.fromOAuth2(localPart + "@example.com", localPart);
        return userRepository.save(user);
    }

    @Test
    @DisplayName("create 는 title 로 세션을 생성하고 scriptText/sentences/favorite 초기값을 부여한다")
    void createPersistsSession() {
        User user = newUser("alice");

        SessionDetailResponse response = sessionService.create(
                user.getId(), new SessionCreateRequest("My Session"));

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("My Session");
        assertThat(response.scriptText()).isEqualTo("");
        assertThat(response.favorite()).isFalse();
        assertThat(response.sentences()).isEmpty();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create 는 blank title 에 대해 VALIDATION_FAILED 를 던진다")
    void createRejectsBlankTitle() {
        User user = newUser("bob");

        assertThatThrownBy(() -> sessionService.create(
                user.getId(), new SessionCreateRequest("   ")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("get 은 미존재 또는 타인 세션에 대해 SESSION_NOT_FOUND 를 던진다")
    void getOtherUsersSessionReturnsSessionNotFound() {
        User owner = newUser("carol");
        User stranger = newUser("dan");
        SessionDetailResponse created = sessionService.create(
                owner.getId(), new SessionCreateRequest("Owned"));

        assertThatThrownBy(() -> sessionService.get(stranger.getId(), created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);

        assertThatThrownBy(() -> sessionService.get(owner.getId(), 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("patch 는 null 필드를 무시하고 다른 필드만 갱신한다")
    void patchIgnoresNullFields() {
        User user = newUser("erin");
        SessionDetailResponse created = sessionService.create(
                user.getId(), new SessionCreateRequest("Original"));

        sessionService.patch(user.getId(), created.id(),
                new SessionPatchRequest(null, "Hello world. Practice English.", true));

        SessionDetailResponse afterNullPatch = sessionService.patch(
                user.getId(), created.id(), new SessionPatchRequest(null, null, null));

        assertThat(afterNullPatch.title()).isEqualTo("Original");
        assertThat(afterNullPatch.scriptText()).isEqualTo("Hello world. Practice English.");
        assertThat(afterNullPatch.favorite()).isTrue();
        assertThat(afterNullPatch.sentences()).hasSize(2);
    }

    @Test
    @DisplayName("patch 는 scriptText 변경 시 sentences 를 통째 교체하고 session 행은 보존한다 (#5)")
    void updateScriptReplacesSentencesPreservingSessionRow() {
        User user = newUser("frank");
        SessionDetailResponse created = sessionService.create(
                user.getId(), new SessionCreateRequest("Replace Me"));

        sessionService.patch(user.getId(), created.id(),
                new SessionPatchRequest(null, "First sentence. Second one!", null));

        em.flush();
        em.clear();

        Long firstSentenceCount = sessionSentenceRepository.count();
        assertThat(firstSentenceCount).isGreaterThanOrEqualTo(2L);

        sessionService.patch(user.getId(), created.id(),
                new SessionPatchRequest(null, "Totally new body.", null));

        em.flush();
        em.clear();

        Session reloaded = sessionRepository.findById(created.id()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(created.id());
        assertThat(reloaded.getScriptText()).isEqualTo("Totally new body.");
        assertThat(reloaded.getSentences())
                .singleElement()
                .extracting(s -> s.getText())
                .isEqualTo("Totally new body.");
    }

    @Test
    @DisplayName("patch 는 title 만 갱신할 때 다른 필드를 건드리지 않는다")
    void patchTitleLeavesOtherFieldsUnchanged() {
        User user = newUser("grace");
        SessionDetailResponse created = sessionService.create(
                user.getId(), new SessionCreateRequest("Old"));
        sessionService.patch(user.getId(), created.id(),
                new SessionPatchRequest(null, "Body sentence.", true));

        SessionDetailResponse renamed = sessionService.patch(
                user.getId(), created.id(),
                new SessionPatchRequest("Renamed", null, null));

        assertThat(renamed.title()).isEqualTo("Renamed");
        assertThat(renamed.favorite()).isTrue();
        assertThat(renamed.scriptText()).isEqualTo("Body sentence.");
    }

    @Test
    @DisplayName("delete 는 미존재 세션에 대해 SESSION_NOT_FOUND 를 던진다")
    void deleteUnknownReturnsSessionNotFound() {
        User user = newUser("henry");

        assertThatThrownBy(() -> sessionService.delete(user.getId(), 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("delete 는 타인 세션을 삭제하지 않고 SESSION_NOT_FOUND 를 던진다")
    void deleteRefusesNonOwnerSession() {
        User owner = newUser("ivy");
        User stranger = newUser("jack");
        SessionDetailResponse created = sessionService.create(
                owner.getId(), new SessionCreateRequest("Mine"));

        assertThatThrownBy(() -> sessionService.delete(stranger.getId(), created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        assertThat(sessionRepository.findById(created.id())).isPresent();
    }

    @Test
    @DisplayName("delete 는 본인 세션을 제거한다")
    void deleteOwnedSession() {
        User user = newUser("kate");
        SessionDetailResponse created = sessionService.create(
                user.getId(), new SessionCreateRequest("Bye"));

        sessionService.delete(user.getId(), created.id());
        em.flush();

        assertThat(sessionRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("list 는 본인의 세션만 updatedAt 내림차순으로 반환한다")
    void listReturnsOwnedSessionsOrderedByUpdatedAtDesc() throws InterruptedException {
        User user = newUser("liam");
        User stranger = newUser("mia");

        SessionDetailResponse older = sessionService.create(
                user.getId(), new SessionCreateRequest("Older"));
        Thread.sleep(25);
        SessionDetailResponse newer = sessionService.create(
                user.getId(), new SessionCreateRequest("Newer"));
        sessionService.create(stranger.getId(), new SessionCreateRequest("Outsider"));

        List<SessionSummaryResponse> mine = sessionService.list(user.getId());

        assertThat(mine).extracting(SessionSummaryResponse::id)
                .containsExactly(newer.id(), older.id());
    }
}
