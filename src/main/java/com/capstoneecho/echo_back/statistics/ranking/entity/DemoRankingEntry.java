package com.capstoneecho.echo_back.statistics.ranking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.capstoneecho.echo_back.pronunciation.feedback.entity.PronunciationFeedback;
// 시연 단계에서 랭킹 화면을 풍성하게 보여주기 위한 가짜 사용자 한 줄.
// 실제 PronunciationFeedback 누적이 충분해질 때까지의 임시 보조 데이터이며, 운영 단계에선
// 행 전체를 비우면 그대로 사라진다. 코드가 아닌 DB 시드로 관리되어 추가/조정에 재배포가 필요 없다.
@Entity
@Table(name = "demo_ranking_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DemoRankingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30, nullable = false)
    private String nickname;

    @Column(nullable = false)
    private double accuracy;

    public static DemoRankingEntry of(String nickname, double accuracy) {
        var e = new DemoRankingEntry();
        e.nickname = nickname;
        e.accuracy = accuracy;
        return e;
    }
}
