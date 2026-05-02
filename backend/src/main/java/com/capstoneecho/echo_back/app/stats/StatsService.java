package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;

public interface StatsService {

    StatsResponse getMe(Long userId);
}
