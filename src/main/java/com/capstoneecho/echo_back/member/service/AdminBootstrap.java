package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 관리자 계정 셋업의 단일 출처. 두 흐름이 있으며 둘 다 부팅 직후에 한 번 호출된다 (멱등).
//
//   promoteConfiguredAtBoot()
//     app.admin.bootstrap-username 으로 지정된 기존 계정을 ROLE_ADMIN 으로 승격한다.
//     로그인 경로의 promoteIfBootstrap() 와 짝을 이루어 인메모리 DB 환경(부팅 시 계정이 없음)도
//     대비한다.
//
//   createSeedAdminIfMissing()
//     app.admin.seed.* 네 필드가 채워졌으면, 그 username 의 계정이 없을 때 새로 만들어 둔다.
//     시연/평가자에게 가입 절차 없이 즉시 admin 권한을 제공하는 용도이며 비밀번호는 BCrypt 로
//     해시되어 영속된다.
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapUsername;
    private final AppProperties.Admin.SeedAdmin seedAdmin;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        AppProperties.Admin admin = appProperties.admin();
        this.bootstrapUsername = admin == null ? null : admin.bootstrapUsername();
        this.seedAdmin = admin == null ? null : admin.seed();
    }

    private boolean bootstrapConfigured() {
        return bootstrapUsername != null && !bootstrapUsername.isBlank();
    }

    private boolean seedConfigured() {
        return seedAdmin != null
                && notBlank(seedAdmin.username())
                && notBlank(seedAdmin.email())
                && notBlank(seedAdmin.password())
                && notBlank(seedAdmin.nickname());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    // 호출자(예: OAuth upsert)의 트랜잭션 안에서 호출돼야 변경이 영속된다.
    // user 가 부트스트랩 대상이고 아직 일반 계정이면 관리자로 승격한다.
    public boolean promoteIfBootstrap(User user) {
        if (!bootstrapConfigured() || user == null
                || !bootstrapUsername.equals(user.getUsername()) || user.isAdmin()) {
            return false;
        }
        user.promoteToAdmin();
        log.info("부트스트랩으로 '{}' 계정을 관리자로 승격했습니다.", bootstrapUsername);
        return true;
    }

    // 부팅 시 이미 가입돼 있는 부트스트랩 계정을 승격한다.
    // 계정이 아직 없으면 첫 로그인 때 promoteIfBootstrap 가 승격하므로 조용히 넘어간다.
    @Transactional
    public void promoteConfiguredAtBoot() {
        if (!bootstrapConfigured()) {
            return;
        }
        userRepository.findByUsername(bootstrapUsername).ifPresent(this::promoteIfBootstrap);
    }

    // 부팅 시 시드 관리자 계정이 없으면 BCrypt 해시 비밀번호와 함께 새로 만든다.
    // 이미 같은 username 의 계정이 있으면 비밀번호를 덮어쓰지 않고 ROLE_ADMIN 승격만 보장한다.
    @Transactional
    public void createSeedAdminIfMissing() {
        if (!seedConfigured()) {
            return;
        }
        userRepository.findByUsername(seedAdmin.username()).ifPresentOrElse(
                existing -> {
                    if (!existing.isAdmin()) {
                        existing.promoteToAdmin();
                        log.info("시드 관리자 '{}' 가 이미 가입돼 있어 ROLE_ADMIN 으로 승격했습니다.",
                                seedAdmin.username());
                    }
                },
                () -> {
                    String hash = passwordEncoder.encode(seedAdmin.password());
                    User created = User.signup(
                            seedAdmin.username(), seedAdmin.email(), hash, seedAdmin.nickname());
                    created.promoteToAdmin();
                    userRepository.save(created);
                    log.info("시드 관리자 '{}' 계정을 새로 생성했습니다.", seedAdmin.username());
                });
    }
}
