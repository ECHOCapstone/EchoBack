package com.capstoneecho.echo_back.pronunciation.phoneme.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.dto.PhonemeArticulationResponse;
import com.capstoneecho.echo_back.pronunciation.phoneme.service.PhonemeArticulationService;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 음소 조음 안내 공개 조회. 이미지는 <img src> 로 직접 불러야 하므로 SecurityConfig 에서 permitAll 이다.
@RestController
@RequestMapping("/api/phonemes")
public class PhonemeController {

    private final PhonemeArticulationService articulationService;

    public PhonemeController(PhonemeArticulationService articulationService) {
        this.articulationService = articulationService;
    }

    // 음소별 조음 안내(음차·설명·이미지 경로) 목록. 프론트 조음 카드가 사용한다.
    @GetMapping
    public ApiResponse<List<PhonemeArticulationResponse>> list() {
        return ApiResponse.success(articulationService.list());
    }

    // 이미지 바이트 그대로 서빙 (봉투 없이). 인벤토리에 없는 음소면 404.
    // 고정 콘텐츠라 장기 캐시를 허용해 매 카드 진입마다 재다운로드하지 않게 한다.
    @GetMapping("/{phoneme}/image")
    public ResponseEntity<byte[]> image(@PathVariable String phoneme) {
        PhonemeArticulationService.ImageData data = articulationService.image(phoneme);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(data.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(data.bytes());
    }
}
