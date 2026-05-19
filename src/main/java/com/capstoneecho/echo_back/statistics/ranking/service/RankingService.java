package com.capstoneecho.echo_back.statistics.ranking.service;

import com.capstoneecho.echo_back.statistics.ranking.dto.RankingResponse;

public interface RankingService {

    RankingResponse today(Long userId);
}
