package com.capstoneecho.echo_back.app.script;

import com.capstoneecho.echo_back.app.track.Track;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Track 안의 한 챕터를 표현한다. title/content 가 학습 단위 메타이고, track + chapterOrder 가
// 같은 트랙 내 순서를 결정한다. isPreset=true 인 스크립트는 시드 데이터 (트랙 자체의 일부),
// false 는 사용자 맞춤 학습 또는 외부 입력에서 만들어진 자유 스크립트다.
//
// practiceWord 는 이 챕터를 끝낸 사용자에게 권장할 한 단어. 챕터마다 의도를 가지고 지정되며
// (R/L 챕터 → light, V/B → vest …), null 이면 LLM/RuleBased 가 자체 규칙으로 결정한다.
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

    // 시드 챕터 vs 사용자 자유 스크립트를 구분. primitive boolean 으로 두어 null 가능성을 차단한다.
    @Column(name = "is_preset", nullable = false)
    private boolean preset;

    // 챕터 종합 피드백 시 권장할 재연습 단어. nullable 이며 비어 있으면 LlmFeedbackGenerator 가 결정한다.
    @Column(name = "practice_word", length = 100)
    private String practiceWord;

    // 이 챕터를 마스터했을 때 사용자에게 부여할 배지의 표시명.
    // 채워져 있으면 BadgePolicy 가 자동으로 "X 마스터" 배지를 만든다 (챕터 = 배지 SSOT).
    // null 이면 마스터 배지 대상이 아님 — 잰말놀이처럼 빈도형 챌린지 챕터는 채우지 않는다.
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
