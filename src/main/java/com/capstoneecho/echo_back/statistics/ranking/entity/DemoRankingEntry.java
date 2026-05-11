package com.capstoneecho.echo_back.statistics.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "demo_ranking_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoRankingEntry {

    private static final int ACCURACY_MIN = 0;
    private static final int ACCURACY_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 30)
    private String nickname;

    @Column(name = "accuracy", nullable = false)
    private int accuracy;

    private DemoRankingEntry(String nickname, int accuracy) {
        this.nickname = nickname;
        this.accuracy = accuracy;
    }

    public static DemoRankingEntry create(String nickname, int accuracy) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname is required");
        }
        if (accuracy < ACCURACY_MIN || accuracy > ACCURACY_MAX) {
            throw new IllegalArgumentException("accuracy must be in [0, 100]");
        }
        return new DemoRankingEntry(nickname, accuracy);
    }
}
