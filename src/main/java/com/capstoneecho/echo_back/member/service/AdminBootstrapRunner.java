package com.capstoneecho.echo_back.member.service;

import com.capstoneecho.echo_back.global.config.AppProperties;
import com.capstoneecho.echo_back.member.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 부팅 후 app.admin.bootstrap-username 계정을 관리자로 승격한다.
// 설정이 비어 있으면 아무것도 하지 않으며, 이미 관리자면 멱등하게 넘어간다.
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final String bootstrapUsername;

    public AdminBootstrapRunner(UserRepository userRepository, AppProperties appProperties) {
        this.userRepository = userRepository;
        AppProperties.Admin admin = appProperties.admin();
        this.bootstrapUsername = admin == null ? null : admin.bootstrapUsername();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()) {
            return;
        }
        userRepository.findByUsername(bootstrapUsername).ifPresentOrElse(
                user -> {
                    if (!user.isAdmin()) {
                        user.promoteToAdmin();
                        log.info("부트스트랩으로 '{}' 계정을 관리자로 승격했습니다.", bootstrapUsername);
                    }
                },
                () -> log.warn(
                        "admin bootstrap username '{}' 에 해당하는 계정이 없어 승격을 건너뜁니다.",
                        bootstrapUsername));
    }
}
