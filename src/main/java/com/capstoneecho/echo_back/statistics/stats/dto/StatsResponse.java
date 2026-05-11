package com.capstoneecho.echo_back.statistics.stats.dto;

import java.util.List;
import java.util.Map;

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
