package com.capstoneecho.echo_back.app.track;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 학습 코스의 최상위 단위. 한 트랙은 순서 있는 챕터(Script) 들의 묶음이고,
// 사용자가 트랙을 선택하면 첫 챕터부터 순차로 학습한다.
@Entity
@Table(name = "tracks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 트랙 목록 노출 시 사용하는 정렬 키. 작은 값이 먼저 표시된다.
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Track create(String title, String description, int displayOrder) {
        var t = new Track();
        t.title = title;
        t.description = description;
        t.displayOrder = displayOrder;
        return t;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
