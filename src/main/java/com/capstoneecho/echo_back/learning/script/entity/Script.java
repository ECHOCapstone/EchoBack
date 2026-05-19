package com.capstoneecho.echo_back.learning.script.entity;

import com.capstoneecho.echo_back.learning.track.entity.Track;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 트랙 안의 한 챕터. preset=true 면 시드 챕터, false 면 사용자가 만든 자유 스크립트다.
// 자유 스크립트는 트랙에 소속되지 않아 track 과 chapterOrder 가 null 일 수 있다.
@Entity
@Table(
        name = "scripts",
        indexes = @Index(name = "ix_scripts_track", columnList = "track_id, chapter_order")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 16)
    private Difficulty difficulty;

    @Column(name = "preset", nullable = false)
    private boolean preset;

    // 종합 피드백에서 권장할 재연습 단어. 비어 있으면 PracticeWordResolver 가 약점 음소로 추정한다.
    @Column(name = "practice_word", length = 100)
    private String practiceWord;

    // 챕터 마스터 배지명. 잰말놀이처럼 횟수로 평가하는 챕터는 비워 둔다.
    @Column(name = "mastery_badge_name", length = 50)
    private String masteryBadgeName;

    // 트랙 안에서의 노출 순서. 자유 스크립트는 트랙 자체가 없어 null.
    @Column(name = "chapter_order")
    private Integer chapterOrder;

    private Script(
            Track track,
            Integer chapterOrder,
            String title,
            String content,
            Difficulty difficulty,
            boolean preset,
            String practiceWord,
            String masteryBadgeName
    ) {
        this.track = track;
        this.chapterOrder = chapterOrder;
        this.title = title;
        this.content = content;
        this.difficulty = difficulty;
        this.preset = preset;
        this.practiceWord = practiceWord;
        this.masteryBadgeName = masteryBadgeName;
    }

    public static Script createChapter(
            Track track,
            Integer chapterOrder,
            String title,
            String content,
            Difficulty difficulty,
            String practiceWord,
            String masteryBadgeName
    ) {
        if (track == null) {
            throw new IllegalArgumentException("track is required for a chapter");
        }
        requireNonBlank(title, "title");
        requireNonBlank(content, "content");
        return new Script(track, chapterOrder, title, content, difficulty, true, practiceWord, masteryBadgeName);
    }

    public static Script createStandalone(String title, String content, Difficulty difficulty) {
        requireNonBlank(title, "title");
        requireNonBlank(content, "content");
        return new Script(null, null, title, content, difficulty, false, null, null);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
