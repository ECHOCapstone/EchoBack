package com.capstoneecho.echo_back.app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 앱 전반의 횡단 설정 모음. 책임이 큰 영역(보안 / HTTP 클라이언트 / MVC)은 각각
// SecurityConfig / HttpClientConfig / WebMvcConfig 로 분리되어 있고, 본 클래스는 AppProperties
// 활성화만 담당한다.
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}
