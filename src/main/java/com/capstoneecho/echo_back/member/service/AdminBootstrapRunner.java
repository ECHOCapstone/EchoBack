package com.capstoneecho.echo_back.member.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 부팅 후 app.admin.bootstrap-username 계정을 관리자로 승격한다 (AdminBootstrap 에 위임).
// 인메모리 DB 처럼 부팅 시점에 계정이 없으면 첫 로그인 때 승격된다.
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrap adminBootstrap;

    public AdminBootstrapRunner(AdminBootstrap adminBootstrap) {
        this.adminBootstrap = adminBootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrap.promoteConfiguredAtBoot();
    }
}
