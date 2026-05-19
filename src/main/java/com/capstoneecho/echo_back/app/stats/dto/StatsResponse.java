package com.capstoneecho.echo_back.app.stats.dto;

import java.util.List;
import java.util.Map;

// /api/stats/me 의 응답.
//   streak/exp     : User 엔티티 누적 값
//   attendance     : year/month + day → 그 날까지의 누적 출석 streak
//   weeklyErrors   : 지난 일주일 동안 가장 많이 틀린 음소 top N
//   badges         : 보유/미보유 배지 목록
public record StatsResponse(
        int streak,
        int exp,
        Attendance attendance,
        List<PhonemeFrequency> weeklyErrors,
        List<Badge> badges
) {

    public record Attendance(int year, int month, Map<Integer, Integer> days) {}

    public record PhonemeFrequency(String sound, int count) {}

    public record Badge(String id, String name, boolean achieved) {}
}
