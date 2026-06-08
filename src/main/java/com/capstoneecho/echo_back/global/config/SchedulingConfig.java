package com.capstoneecho.echo_back.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled 빈 스캔 활성화를 별도 @Configuration 으로 분리한다.
// 메인 클래스에 두면 slice 테스트가 스케줄러까지 끌어와 무관한 부트 부담을 키운다.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
