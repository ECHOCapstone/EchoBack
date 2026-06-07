package com.capstoneecho.echo_back.support;

import com.capstoneecho.echo_back.global.jwt.JwtProvider;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code /api/admin/**} 컨트롤러 통합 테스트 공통 베이스.
 *
 * <p>{@code JwtAuthFilter} 는 매 인증 요청마다 토큰 subject(userId) 로 DB 조회를 수행하고,
 * 사용자가 없거나 탈퇴 상태면 컨텍스트를 비워 익명 요청으로 강등한다. 따라서 테스트 토큰은
 * 반드시 <b>실제로 저장된 사용자</b>의 ID 에 묶여야 한다. 하드코딩된 {@code 1L}/{@code 2L}
 * 토큰은 해당 사용자가 시딩되지 않아 {@code /api/admin/**} 에서 403 이 되어 happy-path
 * 단언이 깨진다.</p>
 *
 * <p>사용자는 username 으로 <b>find-or-create</b> 한다. {@code @Transactional} 테스트
 * (매 메서드 롤백) 와 비-{@code @Transactional} 테스트 (행이 컨텍스트에 잔존) 양쪽에서
 * {@code uk_users_username} / {@code uk_users_email} 제약을 위반하지 않고 같은 사용자를
 * 재사용한다.</p>
 */
public abstract class AbstractAdminControllerIntegrationTest
        extends AbstractControllerIntegrationTest {

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** ROLE_ADMIN 사용자를 find-or-create 하고, 그 실제 ID 에 묶인 ADMIN JWT 를 발급한다. */
    protected String adminToken() {
        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            User created = User.signup(
                    "admin", "admin@test.com", passwordEncoder.encode("Password1!"), "관리자");
            created.promoteToAdmin();
            return userRepository.save(created);
        });
        return jwtProvider.issue(admin.getId(), Map.of(
                "username", admin.getUsername(),
                "email", admin.getEmail(),
                "role", "ADMIN"));
    }

    /** ROLE_USER 사용자를 find-or-create 하고, 그 실제 ID 에 묶인 USER JWT 를 발급한다. */
    protected String userToken() {
        User user = userRepository.findByUsername("user").orElseGet(() -> userRepository.save(
                User.signup("user", "user@test.com", passwordEncoder.encode("Password1!"), "유저")));
        return jwtProvider.issue(user.getId(), Map.of(
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", "USER"));
    }
}
