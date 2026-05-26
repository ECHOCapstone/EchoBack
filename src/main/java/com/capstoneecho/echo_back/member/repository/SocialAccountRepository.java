package com.capstoneecho.echo_back.member.repository;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // OAuth2 콜백 처리의 1차 조회 키. provider 별 UID 가 변하지 않으므로 안정적 식별자.
    //
    // JOIN FETCH s.user 로 연결된 User 까지 즉시 로드한다. GoogleOAuth2UserMapper.upsert() 의
    // @Transactional 은 메서드 종료 시 닫히지만 호출자 (CustomOidcUserService.loadUser 등) 는
    // 트랜잭션 밖에서 반환된 User 의 email / username 등 lazy 필드에 접근하므로, fetch join 없이는
    // LazyInitializationException 이 발생한다.
    @Query("SELECT s FROM SocialAccount s JOIN FETCH s.user "
            + "WHERE s.provider = :provider AND s.providerUid = :providerUid")
    Optional<SocialAccount> findByProviderAndProviderUid(
            @Param("provider") Provider provider,
            @Param("providerUid") String providerUid);

    // 기존 표준 가입 User 가 같은 provider 에 다시 연결을 시도하는지 확인할 때 사용.
    Optional<SocialAccount> findByUserAndProvider(User user, Provider provider);
}
