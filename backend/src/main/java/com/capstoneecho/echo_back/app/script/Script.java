package com.capstoneecho.echo_back.app.script;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 학습 unit 한 묶음의 표제(title) 와 본문(content), 난이도, 사전 정의 여부를 보유한다.
// 학습 unit 한 묶음의 메타. 다음 commit 에서 지용님 사본으로 대체될 예정.
@Entity
@Table(name = "scripts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Script {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Difficulty difficulty;

    @Column(name = "is_preset", nullable = false)
    private Boolean isPreset;

    @Column(name = "created_at", insertable = true, updatable = false)
    private Instant createdAt;

    public static Script create(String title, String content, Difficulty difficulty, boolean isPreset) {
        var s = new Script();
        s.title = title;
        s.content = content;
        s.difficulty = difficulty;
        s.isPreset = isPreset;
        return s;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
