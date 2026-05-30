package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.entity.User;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// app.admin.bootstrap-username 계정을 관리자로 승격하는 단일 출처.
// 두 경로로 승격한다(둘 다 멱등):
//   - 부팅 시(promoteConfiguredAtBoot): 영속 DB 에 이미 가입돼 있던 계정을 승격.
//   - 로그인 시(promoteIfBootstrap): 막 가입/로그인한 계정을 그 자리에서 승격.
// 로그인 경로가 필요한 이유: 인메모리 DB 는 부팅 시점에 계정이 없어 부팅 경로가 건너뛰므로,
// OAuth 로그인(자동 회원가입)이 일어나는 그 순간에 승격해야 한다.
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final String bootstrapUsername;

    public AdminBootstrap(UserRepository userRepository, AppProperties appProperties) {
        this.userRepository = userRepository;
        AppProperties.Admin admin = appProperties.admin();
        this.bootstrapUsername = admin == null ? null : admin.bootstrapUsername();
    }

    private boolean configured() {
        return bootstrapUsername != null && !bootstrapUsername.isBlank();
    }

    // 호출자(예: OAuth upsert)의 트랜잭션 안에서 호출돼야 변경이 영속된다.
    // user 가 부트스트랩 대상이고 아직 일반 계정이면 관리자로 승격한다.
    public boolean promoteIfBootstrap(User user) {
        if (!configured() || user == null
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
        if (!configured()) {
            return;
        }
        userRepository.findByUsername(bootstrapUsername).ifPresent(this::promoteIfBootstrap);
    }
}
