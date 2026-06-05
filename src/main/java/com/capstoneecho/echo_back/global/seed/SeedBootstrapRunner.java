package com.capstoneecho.echo_back.global.seed;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 부팅 시 한 번 트랙 시드를 채우는 ApplicationRunner. 시드 로직 자체는 InitialDataLoader 가 갖고,
// 본 러너는 "부팅 시 자동 실행" 책임만 진다. test 프로파일에서는 등록하지 않아 테스트가 빈 DB 에서 시작한다.
// (InitialDataLoader 빈은 모든 프로파일에 존재해야 TrackPersistService 의 시드 재적용이 동작한다.)
@Component
@Profile("!test")
public class SeedBootstrapRunner implements ApplicationRunner {

    private final InitialDataLoader initialDataLoader;

    public SeedBootstrapRunner(InitialDataLoader initialDataLoader) {
        this.initialDataLoader = initialDataLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialDataLoader.seedTracksIfEmpty();
    }
}
