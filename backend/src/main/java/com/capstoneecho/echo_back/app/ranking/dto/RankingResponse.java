package com.capstoneecho.echo_back.app.ranking.dto;

import java.util.List;

public record RankingResponse(
        String unitTitle,
        int myRank,
        int totalUsers,
        double myAccuracy,
        List<Entry> entries
) {

    public record Entry(int rank, String nickname, double accuracy, boolean isMe) {}
}
