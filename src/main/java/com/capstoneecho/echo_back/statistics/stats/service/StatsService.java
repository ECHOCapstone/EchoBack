package com.capstoneecho.echo_back.statistics.stats.service;

import com.capstoneecho.echo_back.statistics.stats.dto.StatsResponse;

public interface StatsService {

    // year / month 가 null 이면 오늘 기준의 출석 캘린더가 만들어진다.
    // 명시되면 해당 월의 출석만 집계한다. weeklyErrors / badges 는 month 와 무관하게 항상 누적 결과를 반환한다.
    StatsResponse getMe(Long userId, Integer year, Integer month);
}
