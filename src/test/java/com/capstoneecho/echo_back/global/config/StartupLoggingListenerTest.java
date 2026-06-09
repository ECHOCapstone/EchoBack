package com.capstoneecho.echo_back.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.capstoneecho.echo_back.EchoBackApplication;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.support.GenericApplicationContext;

@ExtendWith(OutputCaptureExtension.class)
class StartupLoggingListenerTest {

    private final StartupLoggingListener listener = new StartupLoggingListener();

    @Test
    @DisplayName("부팅 실패 이벤트를 ERROR 와 예외 정보로 기록한다")
    void logsFailureWithException(CapturedOutput output) {
        SpringApplication app = new SpringApplication(EchoBackApplication.class);
        IllegalStateException ex = new IllegalStateException("운영 필수 설정 누락");

        listener.onApplicationEvent(
                new ApplicationFailedEvent(app, new String[0], new GenericApplicationContext(), ex));

        assertThat(output)
                .contains("컨텍스트 부팅 실패")
                .contains("운영 필수 설정 누락")
                .contains("IllegalStateException");
    }

    @Test
    @DisplayName("준비완료 이벤트를 INFO 와 소요시간으로 기록한다")
    void logsReadyWithTimeTaken(CapturedOutput output) {
        SpringApplication app = new SpringApplication(EchoBackApplication.class);

        listener.onApplicationEvent(
                new ApplicationReadyEvent(
                        app, new String[0], new GenericApplicationContext(), Duration.ofMillis(123)));

        assertThat(output).contains("부팅 완료").contains("123 ms");
    }

    @Test
    @DisplayName("부팅 생명주기와 무관한 이벤트는 로깅하지 않는다")
    void ignoresUnrelatedEvents(CapturedOutput output) {
        listener.onApplicationEvent(new ApplicationEvent("source") {});

        assertThat(output).doesNotContain("컨텍스트 부팅 실패").doesNotContain("부팅 완료");
    }
}
