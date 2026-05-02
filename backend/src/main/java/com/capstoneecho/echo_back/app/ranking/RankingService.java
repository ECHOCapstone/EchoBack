package com.capstoneecho.echo_back.app.ranking;

import com.capstoneecho.echo_back.app.ranking.dto.RankingResponse;

public interface RankingService {

    RankingResponse today(Long userId);
}
