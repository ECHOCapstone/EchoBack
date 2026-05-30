package com.capstoneecho.echo_back.pronunciation.phoneme.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.dto.PhonemeAssetResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.service.PhonemeAssetService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 음소 이미지 공개 조회. <img src> 로 직접 불러야 하므로 SecurityConfig 에서 permitAll 이다.
@RestController
@RequestMapping("/api/phonemes")
public class PhonemeController {

    private final PhonemeAssetService phonemeAssetService;

    public PhonemeController(PhonemeAssetService phonemeAssetService) {
        this.phonemeAssetService = phonemeAssetService;
    }

    // 등록된 음소 → 이미지 URL 목록. 프론트가 조음 카드에서 사용한다.
    @GetMapping
    public ApiResponse<List<PhonemeAssetResponse>> list() {
        return ApiResponse.success(phonemeAssetService.list());
    }

    // 이미지 바이트 그대로 서빙 (봉투 없이). 없으면 404.
    @GetMapping("/{phoneme}/image")
    public ResponseEntity<byte[]> image(@PathVariable String phoneme) {
        PhonemeAssetService.ImageData data = phonemeAssetService.image(phoneme);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .body(data.bytes());
    }
}
