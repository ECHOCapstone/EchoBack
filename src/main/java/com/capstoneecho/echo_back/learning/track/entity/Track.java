package com.capstoneecho.echo_back.learning.track.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 학습 코스의 최상위 단위. 한 트랙은 순서 있는 챕터(Script) 들의 묶음이며 사용자가
// 트랙을 선택하면 첫 챕터부터 순차로 학습한다. displayOrder 가 작은 값이 먼저 노출된다.
@Entity
@Table(name = "tracks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    private Track(String title, String description, int displayOrder) {
        this.title = title;
        this.description = description;
        this.displayOrder = displayOrder;
    }

    public static Track create(String title, String description, int displayOrder) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        return new Track(title, description, displayOrder);
    }
}
