package com.capstoneecho.echo_back.member.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// 부팅 후 관리자 셋업을 위임한다 (모두 AdminBootstrap 의 책임).
//   1) 시드 관리자 계정이 설정돼 있으면 부재 시 생성한다.
//   2) bootstrap-username 으로 지정된 기존 계정을 ROLE_ADMIN 으로 승격한다.
// 인메모리 DB 처럼 부팅 시점에 계정이 없으면 OAuth 첫 로그인 때 promoteIfBootstrap 가 승격한다.
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrap adminBootstrap;

    public AdminBootstrapRunner(AdminBootstrap adminBootstrap) {
        this.adminBootstrap = adminBootstrap;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrap.createSeedAdminIfMissing();
        adminBootstrap.promoteConfiguredAtBoot();
    }
}
