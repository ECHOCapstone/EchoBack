package com.capstoneecho.echo_back;

import com.capstoneecho.echo_back.global.config.StartupLoggingListener;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan("com.capstoneecho.echo_back")
public class EchoBackApplication {

    public static void main(String[] args) {
        // 부팅 실패/완료 이벤트를 앱 전용 로거로 남기기 위해 컨텍스트 밖에서 리스너를 등록한다.
        SpringApplication app = new SpringApplication(EchoBackApplication.class);
        app.addListeners(new StartupLoggingListener());
        app.run(args);
    }

    // 시간 의존을 한 빈으로 모아 테스트에서 고정 Clock 으로 갈아끼울 수 있게 한다. UTC 기준.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
