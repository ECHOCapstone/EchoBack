package com.capstoneecho.echo_back.member.repository;

import com.capstoneecho.echo_back.member.entity.Provider;
import com.capstoneecho.echo_back.member.entity.SocialAccount;
import com.capstoneecho.echo_back.member.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // OAuth2 콜백 처리의 1차 조회 키. provider 별 UID 가 변하지 않으므로 안정적 식별자.
    Optional<SocialAccount> findByProviderAndProviderUid(Provider provider, String providerUid);

    // 기존 표준 가입 User 가 같은 provider 에 다시 연결을 시도하는지 확인할 때 사용.
    Optional<SocialAccount> findByUserAndProvider(User user, Provider provider);
}
