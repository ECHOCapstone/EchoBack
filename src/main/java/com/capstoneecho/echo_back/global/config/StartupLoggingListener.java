package com.capstoneecho.echo_back.global.config;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

// 컨텍스트 부팅 생명주기 이벤트를 앱 전용 로거로 기록한다.
// 빈이 아니라 SpringApplication.addListeners() 로 등록 — 컨텍스트 refresh 실패 시에도 동작해야 하기 때문.
public class StartupLoggingListener implements ApplicationListener<ApplicationEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLoggingListener.class);

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationFailedEvent failed) {
            log.error("애플리케이션 컨텍스트 부팅 실패 — 원인 확인 필요", failed.getException());
        } else if (event instanceof ApplicationReadyEvent ready) {
            Duration taken = ready.getTimeTaken();
            log.info("애플리케이션 부팅 완료 — 소요 {} ms", taken == null ? -1 : taken.toMillis());
        }
    }
}
