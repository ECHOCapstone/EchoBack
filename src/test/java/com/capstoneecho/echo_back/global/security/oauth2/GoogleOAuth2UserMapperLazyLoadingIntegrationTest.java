package com.capstoneecho.echo_back.global.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.SocialAccountRepository;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 회귀 테스트.
// 이전 구현은 Case A 에서 SocialAccount.user (LAZY) proxy 를 그대로 반환했고, 호출자
// CustomOidcUserService.loadUser() 가 트랜잭션 밖에서 user.getEmail() / getUsername() 에
// 접근하다가 LazyInitializationException 으로 깨졌다.
//
// SocialAccountRepository.findByProviderAndProviderUid 에 JOIN FETCH s.user 를 적용해
// 반환되는 User 가 이미 초기화된 entity 가 되도록 한 fix 가 회귀하지 않게 한다.
//
// Mockito 단위 테스트는 실제 lazy proxy 를 만들지 않아 이 회귀를 잡지 못하므로 별도의
// @SpringBootTest 가 필요하다.
@SpringBootTest
@ActiveProfiles("test")
class GoogleOAuth2UserMapperLazyLoadingIntegrationTest {

    @Autowired
    private GoogleOAuth2UserMapper mapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    private TransactionTemplate tx;

    @Autowired
    void initTx(PlatformTransactionManager txManager) {
        this.tx = new TransactionTemplate(txManager);
    }

    @Test
    @DisplayName("Case A 재로그인: mapper 가 반환한 User 의 email / username 을 트랜잭션 외부에서 안전하게 읽는다")
    void caseAReturnedUserSurvivesOutsideTransaction() {
        String email = "alice-relogin@gmail.com";
        String sub = "sub-relogin-12345";

        // arrange — User + SocialAccount 를 별도 트랜잭션에서 저장 후 즉시 종료
        tx.executeWithoutResult(status -> {
            User user = userRepository.save(User.fromOAuth2(email, "Alice"));
            socialAccountRepository.save(
                    SocialAccount.create(user, Provider.GOOGLE, sub, email, "old-token"));
        });

        // act — mapper.upsert 가 자기 @Transactional 안에서 끝나고, 호출자는 트랜잭션 밖
        User result = mapper.upsert(
                Map.of("sub", sub, "email", email, "name", "Alice", "email_verified", true),
                "new-token");

        // assert — fetch join 이 빠지면 여기서 LazyInitializationException 으로 터진다.
        // (회귀를 정확히 잡는 지점)
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getUsername()).isEqualTo(email);
        assertThat(result.getNickname()).isEqualTo("Alice");
    }
}
