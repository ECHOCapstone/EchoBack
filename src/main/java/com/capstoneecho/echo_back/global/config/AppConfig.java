package com.capstoneecho.echo_back.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

// AppProperties 활성화 + 학습 시간대 빈 등록.
// 보안 / HTTP / MVC 같은 큰 영역은 각각의 *Config 로 따로 빠져 있다.
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    // streak 와 출석 캘린더가 같은 시간대를 보도록 한 곳에서 ZoneId 를 만들어 공유한다.
    @Bean
    public ZoneId learningZoneId(AppProperties properties) {
        return ZoneId.of(properties.time().zoneId());
    }
}
