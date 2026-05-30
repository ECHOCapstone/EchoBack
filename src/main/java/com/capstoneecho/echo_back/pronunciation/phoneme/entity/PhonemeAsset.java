package com.capstoneecho.echo_back.pronunciation.phoneme.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 음소 한 개의 조음 이미지 매핑. phoneme(대문자 ARPAbet)이 키, 이미지 바이트는 스토리지에 둔다.
@Entity
@Table(name = "phoneme_asset")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhonemeAsset {

    @Id
    @Column(name = "phoneme", length = 8)
    private String phoneme;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PhonemeAsset(String phoneme, String imagePath, String contentType) {
        this.phoneme = phoneme;
        this.imagePath = imagePath;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }

    public static PhonemeAsset of(String phoneme, String imagePath, String contentType) {
        return new PhonemeAsset(phoneme, imagePath, contentType);
    }

    public void update(String imagePath, String contentType) {
        this.imagePath = imagePath;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }
}
