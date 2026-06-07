package com.capstoneecho.echo_back;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.capstoneecho.echo_back")
@EnableJpaAuditing
@EnableScheduling
public class EchoBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(EchoBackApplication.class, args);
    }

    // 시간 의존을 한 빈으로 모아 테스트에서 고정 Clock 으로 갈아끼울 수 있게 한다. UTC 기준.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
