package com.capstoneecho.echo_back.member.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class SocialAccountRepositoryDataJpaTest {

    @Autowired
    private SocialAccountRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private User saveUser(String email, String nickname) {
        return userRepository.save(User.fromOAuth2(email, nickname));
    }

    @Test
    @DisplayName("findByProviderAndProviderUid 는 저장된 SocialAccount 를 반환한다")
    void findByProviderAndProviderUidReturnsPersisted() {
        User user = saveUser("alice@x.com", "Alice");
        repository.save(SocialAccount.create(user, Provider.GOOGLE, "sub-1", "alice@x.com"));

        assertThat(repository.findByProviderAndProviderUid(Provider.GOOGLE, "sub-1"))
                .isPresent()
                .get()
                .satisfies(account -> {
                    assertThat(account.getUser().getId()).isEqualTo(user.getId());
                    assertThat(account.getProviderEmail()).isEqualTo("alice@x.com");
                });
    }

    @Test
    @DisplayName("findByProviderAndProviderUid 는 존재하지 않으면 Optional.empty 를 반환한다")
    void findByProviderAndProviderUidReturnsEmptyWhenAbsent() {
        assertThat(repository.findByProviderAndProviderUid(Provider.GOOGLE, "missing"))
                .isEmpty();
    }

    @Test
    @DisplayName("findByUserAndProvider 는 해당 user/provider 조합의 SocialAccount 를 반환한다")
    void findByUserAndProviderReturnsPersisted() {
        User user = saveUser("bob@x.com", "Bob");
        repository.save(SocialAccount.create(user, Provider.GOOGLE, "sub-2", "bob@x.com"));

        assertThat(repository.findByUserAndProvider(user, Provider.GOOGLE))
                .isPresent()
                .get()
                .extracting(SocialAccount::getProviderUid)
                .isEqualTo("sub-2");
    }

    @Test
    @DisplayName("findByProviderAndProviderUid 는 JOIN FETCH 로 user 까지 즉시 로드한다 (LazyInitializationException 회귀 방지)")
    void findByProviderAndProviderUidEagerlyLoadsUser() {
        User user = saveUser("eager@x.com", "Eager");
        repository.save(SocialAccount.create(user, Provider.GOOGLE, "sub-fj", "eager@x.com"));
        em.flush();
        em.clear();

        SocialAccount loaded = repository.findByProviderAndProviderUid(Provider.GOOGLE, "sub-fj")
                .orElseThrow();
        em.detach(loaded);  // proxy 라면 이 시점 이후 lazy 필드 접근에서 LazyInitializationException

        assertThat(loaded.getUser().getEmail()).isEqualTo("eager@x.com");
        assertThat(loaded.getUser().getUsername()).isEqualTo("eager@x.com");
        assertThat(loaded.getUser().getNickname()).isEqualTo("Eager");
    }

    @Test
    @DisplayName("(provider, providerUid) 동일 키 저장 시 unique 제약 위반으로 실패한다")
    void uniqueConstraintOnProviderAndUid() {
        User userA = saveUser("a@x.com", "A");
        User userB = saveUser("b@x.com", "B");
        repository.saveAndFlush(SocialAccount.create(userA, Provider.GOOGLE, "dup-sub", "a@x.com"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                SocialAccount.create(userB, Provider.GOOGLE, "dup-sub", "b@x.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
