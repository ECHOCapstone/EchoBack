package com.capstoneecho.echo_back.global.config;

import java.time.ZoneId;
import org.springframework.stereotype.Component;

// 통계 / 출석 / 추천 도메인이 공유하는 타임존을 한 곳에서 노출한다.
// app.stats.zone 미설정 시 시스템 zone 으로 폴백한다.
@Component
public class StatsZoneProvider {

    private final AppProperties appProperties;

    public StatsZoneProvider(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ZoneId zone() {
        AppProperties.Stats stats = appProperties.stats();
        return stats == null ? ZoneId.systemDefault() : stats.resolvedZone();
    }
}
