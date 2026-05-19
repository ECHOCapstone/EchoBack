package com.capstoneecho.echo_back.learning.script.entity;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import com.capstoneecho.echo_back.pronunciation.feedback.support.PracticeWordResolver;
import com.capstoneecho.echo_back.statistics.stats.support.BadgePolicy;
// 트랙 안의 한 챕터. preset=true 면 시드 챕터, false 면 사용자가 만든 자유 스크립트.
@Entity
@Table(name = "scripts", indexes = @Index(name = "ix_scripts_track", columnList = "track_id, chapter_order"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @Column(name = "chapter_order")
    private Integer chapterOrder;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Difficulty difficulty;

    @Column(name = "is_preset", nullable = false)
    private boolean preset;

    // 종합 피드백에서 권장할 재연습 단어. 비어 있으면 PracticeWordResolver 가 약점 음소로 추정한다.
    @Column(name = "practice_word", length = 100)
    private String practiceWord;

    // 이 챕터를 마스터했을 때 부여할 배지명. 채워져 있으면 BadgePolicy 가 자동으로 마스터 배지를 만든다.
    // 잰말놀이처럼 횟수로 평가하는 챕터는 비워 둔다.
    @Column(name = "mastery_badge_name", length = 50)
    private String masteryBadgeName;

    @Column(name = "created_at", insertable = true, updatable = false)
    private Instant createdAt;

    public static Script createChapter(
            Track track,
            int chapterOrder,
            String title,
            String content,
            Difficulty difficulty,
            String practiceWord,
            String masteryBadgeName
    ) {
        var s = new Script();
        s.track = track;
        s.chapterOrder = chapterOrder;
        s.title = title;
        s.content = content;
        s.difficulty = difficulty;
        s.preset = true;
        s.practiceWord = practiceWord;
        s.masteryBadgeName = masteryBadgeName;
        return s;
    }

    public static Script createStandalone(String title, String content, Difficulty difficulty) {
        var s = new Script();
        s.title = title;
        s.content = content;
        s.difficulty = difficulty;
        s.preset = false;
        return s;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
